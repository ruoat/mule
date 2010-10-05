/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.transport;

import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.MuleSession;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.transaction.Transaction;
import org.mule.api.transaction.TransactionCallback;
import org.mule.api.transaction.TransactionException;
import org.mule.api.transport.SessionHandler;
import org.mule.session.LegacySessionHandler;
import org.mule.session.MuleSessionHandler;
import org.mule.transaction.TransactionCoordination;
import org.mule.transaction.TransactionTemplate;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.resource.spi.work.Work;

import org.apache.commons.lang.SerializationException;

/**
 * A base Worker used by Transport {@link org.mule.api.transport.MessageReceiver} implementations.
 */
public abstract class AbstractReceiverWorker implements Work
{
    protected List<Object> messages;
    protected InboundEndpoint endpoint;
    protected AbstractMessageReceiver receiver;
    protected OutputStream out;

    public AbstractReceiverWorker(List<Object> messages, AbstractMessageReceiver receiver)
    {
        this(messages, receiver, null);
    }

    public AbstractReceiverWorker(List<Object> messages, AbstractMessageReceiver receiver, OutputStream out)
    {
        this.messages = messages;
        this.receiver = receiver;
        this.endpoint = receiver.getEndpoint();
        this.out = out;
    }

    /**
     * This will run the receiver logic and call {@link #release()} once {@link #doRun()} completes.
    *
    */
    public final void run()
    {
        doRun();
        release();
    }

    /**
     * The actual logic used to receive messages from the underlying transport.  The default implementation
     * will execute the processing of messages within a TransactionTemplate.  This template will manage the
     * transaction lifecycle for the list of messages associated with this receiver worker.
     */
    protected void doRun()
    {
        //  MuleContext is used down the line for
        // getTransactionManager() (XaTransactionFactory) and getQueueManager() (VMTransaction)
        final MuleContext muleContext = receiver.getConnector().getMuleContext();
        TransactionTemplate tt = new TransactionTemplate(endpoint.getTransactionConfig(), muleContext);

        // Receive messages and process them in a single transaction
        // Do not enable threading here, but serveral workers
        // may have been started
        TransactionCallback<?> cb = new TransactionCallback()
        {
            public Object doInTransaction() throws Exception
            {
                Transaction tx = TransactionCoordination.getInstance().getTransaction();
                if (tx != null)
                {
                    bindTransaction(tx);
                }
                List<Object> results = new ArrayList<Object>(messages.size());

                for (Object payload : messages)
                {
                    payload = preProcessMessage(payload);
                    if (payload != null)
                    {
                        MuleMessage muleMessage = receiver.createMuleMessage(payload, endpoint.getEncoding());
                        preRouteMuleMessage(muleMessage);

                        // TODO Move getSessionHandler() to the Connector interface
                        SessionHandler handler;
                        if (endpoint.getConnector() instanceof AbstractConnector)
                        {
                            handler = ((AbstractConnector) endpoint.getConnector()).getSessionHandler();
                        }
                        else
                        {
                            handler = new MuleSessionHandler();
                        }
                        MuleSession session;
                        try
                        {
                            session = handler.retrieveSessionInfoFromMessage(muleMessage);
                        }
                        catch (SerializationException e)
                        {
                            // EE-1820 Support message headers generated by previous Mule versions
                            session = new LegacySessionHandler().retrieveSessionInfoFromMessage(muleMessage);
                        }
                        
                        MuleEvent resultEvent;
                        if (session != null)
                        {
                            resultEvent = receiver.routeMessage(muleMessage, session, tx, out);
                        }
                        else
                        {
                            resultEvent = receiver.routeMessage(muleMessage, tx, out);
                        }
                        MuleMessage result = resultEvent == null ?  null : resultEvent.getMessage();
                        if (result != null)
                        {
                            payload = postProcessMessage(result);
                            if (payload != null)
                            {
                                results.add(payload);
                            }
                        }
                    }
                }
                return results;
            }
        };

        try
        {
            List results = (List) tt.execute(cb);
            handleResults(results);
        }
        catch (Exception e)
        {
            muleContext.getExceptionListener().handleException(e);
        }
        finally
        {
            messages.clear();
        }
    }

    /**
     * This callback is called before a message is routed into Mule and can be used by the worker to set connection
     * specific properties to message before it gets routed
     *
     * @param message the next message to be processed
     * @throws Exception
     */
    protected void preRouteMuleMessage(MuleMessage message) throws Exception
    {
        //no op
    }

    /**
     * Template method used to bind the resources of this receiver to the transaction.  Only transactional
     * transports need implment this method
     * @param tx the current transaction or null if there is no transaction
     * @throws TransactionException
     */
    protected abstract void bindTransaction(Transaction tx) throws TransactionException;

    /**
     * When Mule has finished processing the current messages, there may be zero or more messages to process
     * by the receiver if request/response messaging is being used. The result(s) should be passed back to the callee.
     * @param messages a list of messages.  This argument will not be null
     * @throws Exception
     */
    protected void handleResults(List messages) throws Exception
    {
        //no op
    }

    /**
     * Before a message is passed into Mule this callback is called and can be used by the worker to inspect the
     * message before it gets sent to Mule
     * @param message the next message to be processed
     * @return the message to be processed. If Null is returned the message will not get processed.
     * @throws Exception
     */
    protected Object preProcessMessage(Object message) throws Exception
    {
        //no op
        return message;
    }

    /**
     * If a result is returned back this method will get called before the message is added to te list of
     * results (these are later passed to {@link #handleResults(java.util.List)})
     * @param message the result message, this will never be null
     * @return the message to add to the list of results. If null is returned nothing is added to the
     * list of results
     * @throws Exception
     */
    protected MuleMessage postProcessMessage(MuleMessage message) throws Exception
    {
        //no op
        return message;
    }


    /**
     * This method is called once this worker is no longer required.  Any resources *only* associated with
     * this worker should be cleaned up here.
     */
    public void release()
    {
        // no op
    }
}

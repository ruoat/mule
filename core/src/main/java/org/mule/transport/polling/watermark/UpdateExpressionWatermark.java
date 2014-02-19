/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.polling.watermark;

import org.mule.api.MuleEvent;
import org.mule.api.store.ObjectStore;
import org.mule.transport.polling.MessageProcessorPollingInterceptor;

import java.io.NotSerializableException;
import java.io.Serializable;

import org.apache.commons.lang.StringUtils;

/**
 * Implementation of {@link Watermark} in which the value is updated through a MEL
 * expression
 */
public class UpdateExpressionWatermark extends Watermark
{

    /**
     * The update expression to update the watermark value in the object store. It is
     * optional so it can be null.
     */
    private final String updateExpression;
    private final MessageProcessorPollingInterceptor interceptor;

    public UpdateExpressionWatermark(ObjectStore<Serializable> objectStore,
                                     String variable,
                                     String defaultExpression,
                                     String updateExpression)
    {
        super(objectStore, variable, defaultExpression);
        this.updateExpression = updateExpression;
        this.interceptor = new WatermarkPollingInterceptor(this);
    }

    /**
     * Returns the new watermark value by evaluating {@link #updateExpression} on the
     * flowVar of the given name
     * 
     * @param event the @{link {@link MuleEvent} in which the watermark is being
     *            evaluated
     * @return a {@link Serializable} value
     */
    @Override
    protected Object getUpdatedValue(MuleEvent event)
    {
        try
        {
            return StringUtils.isEmpty(this.updateExpression)
                   ? event.getFlowVariable(this.resolveVariable(event))
                   : WatermarkUtils.evaluate(this.updateExpression,
                                             event);
        }
        catch (NotSerializableException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public MessageProcessorPollingInterceptor interceptor()
    {
        return this.interceptor;
    }
}

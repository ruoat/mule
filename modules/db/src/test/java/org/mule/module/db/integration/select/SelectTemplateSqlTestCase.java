/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.db.integration.select;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.mule.module.db.domain.param.InputQueryParam;
import org.mule.module.db.domain.query.QueryTemplate;
import org.mule.module.db.domain.query.QueryType;
import org.mule.module.db.domain.type.UnknownDbType;
import org.mule.tck.junit4.FunctionalTestCase;

import org.junit.Test;

public class SelectTemplateSqlTestCase extends FunctionalTestCase
{

    @Override
    protected String getConfigFile()
    {
        return "integration/select/select-template-query-config.xml";
    }

    @Test
    public void configuresSimpleQuery() throws Exception
    {
        Object queryTemplateBean = muleContext.getRegistry().get("simple");
        assertTrue(queryTemplateBean instanceof QueryTemplate);
        QueryTemplate queryTemplate = (QueryTemplate) queryTemplateBean;
        assertEquals(QueryType.SELECT, queryTemplate.getType());
        assertEquals(0, queryTemplate.getInputParams().size());
    }

    @Test
    public void configuresParameterizedQuery() throws Exception
    {
        Object queryTemplateBean = muleContext.getRegistry().get("parameterized");
        assertTrue(queryTemplateBean instanceof QueryTemplate);
        QueryTemplate queryTemplate = (QueryTemplate) queryTemplateBean;
        assertEquals(QueryType.SELECT, queryTemplate.getType());
        assertEquals(1, queryTemplate.getInputParams().size());

        InputQueryParam inputSqlParam = queryTemplate.getInputParams().get(0);
        assertEquals(UnknownDbType.getInstance(), inputSqlParam.getType());
    }
}

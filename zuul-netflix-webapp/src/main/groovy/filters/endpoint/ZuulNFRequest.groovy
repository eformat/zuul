/*
 * Copyright 2013 Netflix, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 */
package filters.endpoint

import com.netflix.zuul.context.*
import com.netflix.zuul.exception.ZuulException
import com.netflix.zuul.filters.SyncEndpoint
import com.netflix.zuul.origins.Origin
import com.netflix.zuul.origins.OriginManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.runners.MockitoJUnitRunner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static org.junit.Assert.assertEquals

class ZuulNFRequest extends SyncEndpoint<HttpRequestMessage, HttpResponseMessage>
{
    private static final Logger LOG = LoggerFactory.getLogger(ZuulNFRequest.class);

    @Override
    HttpResponseMessage apply(HttpRequestMessage request)
    {
        Attributes attrs = request.getContext().getAttributes()

        debug(request.getContext(), request)

        // Get the Origin.
        String name = attrs.getRouteVIP()
        OriginManager originManager = request.getContext().getHelpers().get("origin_manager")
        Origin origin = originManager.getOrigin(name)
        if (origin == null) {
            throw new ZuulException("No Origin registered for name=${name}!", 500, "UNKNOWN_VIP")
        }

        // Add execution of the request to the Observable chain, and block waiting for it to finish.
        HttpResponseMessage response = origin.request(request).toBlocking().first()

        return response
    }

    void debug(SessionContext context, HttpRequestMessage request) {

        if (Debug.debugRequest(context)) {

            request.getHeaders().entries().each {
                Debug.addRequestDebug(context, "ZUUL:: > ${it.key}  ${it.value}")
            }
            String query = ""
            request.getQueryParams().entries().each {
                query += it.key + "=" + it.value + "&"
            }

            Debug.addRequestDebug(context, "ZUUL:: > ${request.getMethod()}  ${request.getPath()}?${query} ${request.getProtocol()}")

            if (request.getBody() != null) {
                if (!Debug.debugRequestHeadersOnly()) {
                    String entity = new ByteArrayInputStream(request.getBody()).getText()
                    Debug.addRequestDebug(context, "ZUUL:: > ${entity}")
                }
            }
        }
    }


    @RunWith(MockitoJUnitRunner.class)
    public static class TestUnit
    {
        @Mock
        HttpResponseMessage response
        @Mock
        HttpRequestMessage request

        SessionContext ctx

        @Before
        public void setup()
        {
            ctx = new SessionContext()
            Mockito.when(request.getContext()).thenReturn(ctx)
            response = new HttpResponseMessage(ctx, request, 200)
        }

        @Test
        public void testDebug()
        {
            ZuulNFRequest filter = new ZuulNFRequest()
            ctx.getAttributes().setDebugRequest(true)

            Headers headers = new Headers()
            headers.add("lah", "deda")

            HttpQueryParams params = new HttpQueryParams()
            params.add("k1", "v1")

            HttpRequestMessage request = new HttpRequestMessage(ctx, "HTTP/1.1", "POST", "/some/where", params, headers, "9.9.9.9", "https")

            filter.debug(ctx, request)

            List<String> debugLines = Debug.getRequestDebug(ctx)
            assertEquals(2, debugLines.size())
            assertEquals("ZUUL:: > lah  deda", debugLines.get(0))
            assertEquals("ZUUL:: > POST  /some/where?k1=v1& HTTP/1.1", debugLines.get(1))
        }
    }

}



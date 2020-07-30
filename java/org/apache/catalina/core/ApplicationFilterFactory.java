/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.catalina.core;

import org.apache.catalina.Globals;
import org.apache.catalina.Wrapper;
import org.apache.catalina.connector.Request;
import org.apache.tomcat.util.descriptor.web.FilterMap;

import javax.servlet.DispatcherType;
import javax.servlet.Servlet;
import javax.servlet.ServletRequest;

/**
 * Factory for the creation and caching of Filters and creation
 * of Filter Chains.
 *
 * @author Greg Murray
 * @author Remy Maucherat
 */
public final class ApplicationFilterFactory {

    private ApplicationFilterFactory() {
        // Prevent instance creation. This is a utility class.
    }


    /**
     * Construct a FilterChain implementation that will wrap the execution of
     * the specified servlet instance.
     * 构造一个FilterChain实现，它将封装指定servlet实例的执行。
     *
     * @param request The servlet request we are processing  我们正在处理的servlet请求，javax.servlet.ServletRequest
     * @param wrapper The wrapper managing the servlet instance  管理servlet实例的包装器  org.apache.catalina.Wrapper
     * @param servlet The servlet instance to be wrapped  要包装的servlet实例 javax.servlet.Servlet
     *
     * @return The configured FilterChain instance or null if none is to be executed.
     * 配置的FilterChain实例，如果不执行，则为null。
     */
    public static ApplicationFilterChain createFilterChain(ServletRequest request, Wrapper wrapper, Servlet servlet) {

        // 如果没有要执行的servlet，则返回null
        // If there is no servlet to execute, return null
        if (servlet == null) {
            return null;
        }

        // 创建并初始化过滤器链对象
        // Create and initialize a filter chain object
        ApplicationFilterChain filterChain = null;
        // 这里的Request为org.apache.catalina.connector.Request
        if (request instanceof Request) {
            Request req = (Request) request;
            // SecurityManager是否打开
            if (Globals.IS_SECURITY_ENABLED) {
                // Security: Do not recycle 不回收
                filterChain = new ApplicationFilterChain();
            } else {
                // 从请求中获取ApplicationFilterChain，也就是回收的
                filterChain = (ApplicationFilterChain) req.getFilterChain();
                if (filterChain == null) {
                    filterChain = new ApplicationFilterChain();
                    req.setFilterChain(filterChain);
                }
            }
        } else {
            // Request dispatcher in use
            // 正在使用的请求调度程序，ApplicationDispatcher调用过来的
            filterChain = new ApplicationFilterChain();
        }

        //设置将在这个链的末尾执行的servlet。
        filterChain.setServlet(servlet);
        //关联的servlet实例是否支持异步处理
        filterChain.setServletSupportsAsync(wrapper.isAsyncSupported());

        // Acquire the filter mappings for this Context
        // 获取此上下文的过滤器映射
        StandardContext context = (StandardContext) wrapper.getParent();
        FilterMap filterMaps[] = context.findFilterMaps();

        // If there are no filter mappings, we are done
        // 如果没有过滤器映射，我们就完成了
        if ((filterMaps == null) || (filterMaps.length == 0)) {
            return filterChain;
        }

        // Acquire the information we will need to match filter mappings
        // 获取匹配筛选器映射所需的信息
        DispatcherType dispatcher = (DispatcherType) request.getAttribute(Globals.DISPATCHER_TYPE_ATTR);

        String requestPath = null;
        Object attribute = request.getAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR);
        if (attribute != null){
            requestPath = attribute.toString();
        }

        //返回描述此容器的名称字符串
        String servletName = wrapper.getName();

        // Add the relevant path-mapped filters to this filter chain
        // 将相关的路径映射过滤器添加到此过滤器链
        for (FilterMap filterMap : filterMaps) {
            // dispatcher类型与FilterMap中指定的dispatcher类型是否匹配
            if (!matchDispatcher(filterMap, dispatcher)) {
                continue;
            }
            //上下文相关的请求路径符合指定的筛选器映射的要求
            if (!matchFiltersURL(filterMap, requestPath)) {
                continue;
            }
            //从上下文context中获取ApplicationFilterConfig
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig) context.findFilterConfig(filterMap.getFilterName());
            if (filterConfig == null) {
                // FIXME - log configuration problem
                continue;
            }
            //将过滤器添加到将在此链中执行的过滤器集中。
            filterChain.addFilter(filterConfig);
        }

        // 添加与servlet名称第二次匹配的过滤器
        // Add filters that match on servlet name second
        for (FilterMap filterMap : filterMaps) {
            //dispatcher类型与FilterMap中指定的dispatcher类型是否匹配
            if (!matchDispatcher(filterMap, dispatcher)) {
                continue;
            }
            //如果指定的servlet名称符合指定的筛选器映射的要求
            if (!matchFiltersServlet(filterMap, servletName)) {
                continue;
            }
            //从上下文context中获取ApplicationFilterConfig
            ApplicationFilterConfig filterConfig = (ApplicationFilterConfig) context.findFilterConfig(filterMap.getFilterName());
            if (filterConfig == null) {
                // FIXME - log configuration problem
                continue;
            }
            filterChain.addFilter(filterConfig);
        }

        // Return the completed filter chain
        //将过滤器添加到将在此链中执行的过滤器集中。
        return filterChain;
    }


    // -------------------------------------------------------- Private Methods


    /**
     * Return <code>true</code> if the context-relative request path
     * matches the requirements of the specified filter mapping;
     * otherwise, return <code>false</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param requestPath Context-relative request path of this request
     */
    private static boolean matchFiltersURL(FilterMap filterMap, String requestPath) {

        // Check the specific "*" special URL pattern, which also matches
        // named dispatches
        if (filterMap.getMatchAllUrlPatterns())
            return true;

        if (requestPath == null)
            return false;

        // Match on context relative request path
        String[] testPaths = filterMap.getURLPatterns();

        for (String testPath : testPaths) {
            if (matchFiltersURL(testPath, requestPath)) {
                return true;
            }
        }

        // No match
        return false;

    }


    /**
     * Return <code>true</code> if the context-relative request path
     * matches the requirements of the specified filter mapping;
     * otherwise, return <code>false</code>.
     *
     * @param testPath URL mapping being checked
     * @param requestPath Context-relative request path of this request
     */
    private static boolean matchFiltersURL(String testPath, String requestPath) {

        if (testPath == null)
            return false;

        // Case 1 - Exact Match
        if (testPath.equals(requestPath))
            return true;

        // Case 2 - Path Match ("/.../*")
        if (testPath.equals("/*"))
            return true;
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, requestPath, 0,
                                       testPath.length() - 2)) {
                if (requestPath.length() == (testPath.length() - 2)) {
                    return true;
                } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                    return true;
                }
            }
            return false;
        }

        // Case 3 - Extension Match
        if (testPath.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash)
                && (period != requestPath.length() - 1)
                && ((requestPath.length() - period)
                    == (testPath.length() - 1))) {
                return testPath.regionMatches(2, requestPath, period + 1,
                                               testPath.length() - 2);
            }
        }

        // Case 4 - "Default" Match
        return false; // NOTE - Not relevant for selecting filters

    }


    /**
     * Return <code>true</code> if the specified servlet name matches
     * the requirements of the specified filter mapping; otherwise
     * return <code>false</code>.
     *
     * @param filterMap Filter mapping being checked
     * @param servletName Servlet name being checked
     */
    private static boolean matchFiltersServlet(FilterMap filterMap,
                                        String servletName) {

        if (servletName == null) {
            return false;
        }
        // Check the specific "*" special servlet name
        else if (filterMap.getMatchAllServletNames()) {
            return true;
        } else {
            String[] servletNames = filterMap.getServletNames();
            for (String name : servletNames) {
                if (servletName.equals(name)) {
                    return true;
                }
            }
            return false;
        }

    }


    /**
     * Convenience method which returns true if  the dispatcher type
     * matches the dispatcher types specified in the FilterMap
     */
    private static boolean matchDispatcher(FilterMap filterMap, DispatcherType type) {
        switch (type) {
            case FORWARD :
                if ((filterMap.getDispatcherMapping() & FilterMap.FORWARD) != 0) {
                    return true;
                }
                break;
            case INCLUDE :
                if ((filterMap.getDispatcherMapping() & FilterMap.INCLUDE) != 0) {
                    return true;
                }
                break;
            case REQUEST :
                if ((filterMap.getDispatcherMapping() & FilterMap.REQUEST) != 0) {
                    return true;
                }
                break;
            case ERROR :
                if ((filterMap.getDispatcherMapping() & FilterMap.ERROR) != 0) {
                    return true;
                }
                break;
            case ASYNC :
                if ((filterMap.getDispatcherMapping() & FilterMap.ASYNC) != 0) {
                    return true;
                }
                break;
        }
        return false;
    }
}

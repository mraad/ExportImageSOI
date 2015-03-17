package com.esri;

import com.esri.arcgis.addinframework.TypeChecker;
import com.esri.arcgis.interop.AutomationException;
import com.esri.arcgis.server.IServerObject;
import com.esri.arcgis.server.IServerObjectExtension;
import com.esri.arcgis.server.IServerObjectExtensionManager;
import com.esri.arcgis.server.IServerObjectExtensionManagerProxy;
import com.esri.arcgis.server.IServerObjectHelper;
import com.esri.arcgis.system.Cleaner;
import com.esri.arcgis.system.ILog;
import com.esri.arcgis.system.IPropertySet;
import com.esri.arcgis.system.IRESTRequestHandler;
import com.esri.arcgis.system.IRESTRequestHandlerProxy;
import com.esri.arcgis.system.IRequestHandler;
import com.esri.arcgis.system.IRequestHandler2;
import com.esri.arcgis.system.IRequestHandler2Proxy;
import com.esri.arcgis.system.IRequestHandlerProxy;
import com.esri.arcgis.system.IServerEnvironment;
import com.esri.arcgis.system.IWebRequestHandler;
import com.esri.arcgis.system.IWebRequestHandlerProxy;
import com.esri.arcgis.system.ServerUtilities;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * For an SOE to act as in interceptor, it needs to implement all request handler interfaces:
 * IRESTRequestHandler, IWebRequestHandler, IRequestHandler2, IRequestHandler
 * now the SOE/SOI can intercept all types of calls to ArcObjects or custom SOEs.
 */
public abstract class AbstractSOI implements
        IServerObjectExtension,
        IRESTRequestHandler,
        IWebRequestHandler,
        IRequestHandler2
{
    protected ILog log;
    protected IServerObjectHelper serverObjectHelper;
    protected IServerObject serverObject;
    protected IServerEnvironment serverEnvironment;
    protected IRESTRequestHandler restRequestHandler;
    protected Map<String, IServerObjectExtension> extensionCache = new HashMap<>(); //cache to store SOE references

    public void init(IServerObjectHelper soh) throws IOException, AutomationException
    {
        this.log = ServerUtilities.getServerLogger();
        this.serverEnvironment = ServerUtilities2.getServerEnvironment();
        this.serverObjectHelper = soh;
        this.serverObject = soh.getServerObject();
        this.restRequestHandler = new IRESTRequestHandlerProxy(this.serverObject);
    }

    @Override
    public String getSchema() throws IOException, AutomationException
    {
        final IRESTRequestHandler handler = findRestRequestHandlerDelegate();
        return handler == null ? null : handler.getSchema();
    }

    @Override
    public String handleStringRequest(String capabilities, String request)
            throws IOException, AutomationException
    {
        final IRequestHandler handler = findRequestHandlerDelegate();
        return handler == null ? null : handler.handleStringRequest(capabilities, request);
    }

    @Override
    public byte[] handleStringWebRequest(int httpMethod,
            String requestURL,
            String queryString,
            String capabilities,
            String requestData,
            String[] responseContentType,
            int[] respDataType)
            throws IOException, AutomationException
    {
        final IWebRequestHandler handler = findWebRequestHandlerDelegate();
        return handler == null ? null : handler.handleStringWebRequest(httpMethod,
                requestURL, queryString, capabilities, requestData,
                responseContentType, respDataType);
    }

    @Override
    public byte[] handleBinaryRequest2(String capabilities, byte[] request)
            throws IOException, AutomationException
    {
        final IRequestHandler2 handler = findRequestHandler2Delegate();
        return handler == null ? null : handler.handleBinaryRequest2(capabilities, request);
    }

    @Override
    public byte[] handleBinaryRequest(byte[] request)
            throws IOException, AutomationException
    {
        final IRequestHandler handler = findRequestHandlerDelegate();
        return handler == null ? null : handler.handleBinaryRequest(request);
    }

    protected abstract void preShutdown();

    public void shutdown() throws IOException, AutomationException
    {
        preShutdown();
        Cleaner.release(restRequestHandler);
        Cleaner.release(serverObject);
        Cleaner.release(serverObjectHelper);
        Cleaner.release(serverEnvironment);
        Cleaner.release(log);
    }

    /**
     * Utility functions that return the appropriate delegate interface based on input request.
     *
     * @return the IRESTRequestHandler
     */
    protected IRESTRequestHandler findRestRequestHandlerDelegate() throws IOException
    {
        final IPropertySet props = serverEnvironment.getProperties();
        // Check if there is an extension name set
        String extensionName;
        try
        {
            extensionName = (String) props.getProperty("ExtensionName");
        }
        catch (Exception e)
        {
            extensionName = null;
        }
        if (extensionName == null || extensionName.isEmpty())
        {
            return restRequestHandler;
        }
        else
        {
            // Get the extension reference from cache if available
            if (extensionCache.containsKey(extensionName))
            {
                return new IRESTRequestHandlerProxy(extensionCache.get(extensionName));
            }
            // This request is to be made on a specific extension
            // so we find the extension from the extension manager
            final IServerObjectExtensionManager extnMgr = new IServerObjectExtensionManagerProxy(serverObject);
            final IServerObjectExtension soe = extnMgr.findExtensionByTypeName(extensionName);
            if (TypeChecker.instanceOf(soe, IRESTRequestHandler.class))
            {
                extensionCache.put(extensionName, soe);
                return new IRESTRequestHandlerProxy(soe);
            }
            else
            {
                return null;
            }
        }
    }

    /**
     * Utility functions that return the appropriate delegate interface based on input request.
     *
     * @return the IRequestHandler
     */
    @SuppressWarnings("deprecation")
    protected IRequestHandler findRequestHandlerDelegate() throws IOException
    {
        // Get the server environment
        // IServerEnvironment2 env = getServerEnvironment();
        // Get the environment properties
        IPropertySet props = serverEnvironment.getProperties();
        // Check if there is an extension name set
        String extensionName;
        try
        {
            extensionName = (String) props.getProperty("ExtensionName");
        }
        catch (Exception e)
        {
            extensionName = null;
        }
        if (extensionName == null || extensionName.isEmpty())
        {
            // No extension has been set - return reference to parent parent server object
            if (TypeChecker.instanceOf(serverObject, IRequestHandler.class))
            {
                return new IRequestHandlerProxy(serverObject);
            }
            else
            {
                return null;
            }
        }
        else
        {
            // Get the extension reference from cache if available
            if (extensionCache.containsKey(extensionName))
            {
                return new IRequestHandlerProxy(extensionCache.get(extensionName));
            }
            // This request is to be made on a specific extension
            // so we find the extension from the extension manager
            IServerObjectExtensionManager extnMgr = new IServerObjectExtensionManagerProxy(serverObject);
            IServerObjectExtension soe = extnMgr.findExtensionByTypeName(extensionName);
            if (TypeChecker.instanceOf(soe, IRequestHandler.class))
            {
                extensionCache.put(extensionName, soe);
                return new IRequestHandlerProxy(soe);
            }
            else
            {
                return null;
            }
        }
    }

    /**
     * Utility functions that return the appropriate delegate interface based on input request.
     *
     * @return the IRequestHandler2
     */
    @SuppressWarnings("deprecation")
    protected IRequestHandler2 findRequestHandler2Delegate() throws IOException
    {
        // Get the server environment
        // IServerEnvironment2 env = getServerEnvironment();
        // Get the environment properties
        IPropertySet props = serverEnvironment.getProperties();
        // Check if there is an extension name set
        String extensionName;
        try
        {
            extensionName = (String) props.getProperty("ExtensionName");
        }
        catch (Exception e)
        {
            extensionName = null;
        }
        if (extensionName == null || extensionName.isEmpty())
        {
            // No extension has been set - return reference to parent parent server object
            if (TypeChecker.instanceOf(serverObject, IRequestHandler2.class))
            {
                return new IRequestHandler2Proxy(serverObject);
            }
            else
            {
                return null;
            }
        }
        else
        {
            // Get the extension reference from cache if available
            if (extensionCache.containsKey(extensionName))
            {
                return new IRequestHandler2Proxy(extensionCache.get(extensionName));
            }
            // This request is to be made on a specific extension
            // so we find the extension from the extension manager
            IServerObjectExtensionManager extnMgr = new IServerObjectExtensionManagerProxy(serverObject);
            IServerObjectExtension soe = extnMgr.findExtensionByTypeName(extensionName);
            if (TypeChecker.instanceOf(soe, IRequestHandler2.class))
            {
                extensionCache.put(extensionName, soe);
                return new IRequestHandler2Proxy(soe);
            }
            else
            {
                return null;
            }
        }
    }

    /**
     * Utility functions that return the appropriate delegate interface based on input request.
     *
     * @return the IWebRequestHandler
     */
    @SuppressWarnings("deprecation")
    protected IWebRequestHandler findWebRequestHandlerDelegate() throws IOException
    {
        // Get the server environment
        // Get the environment properties
        IPropertySet props = serverEnvironment.getProperties();
        // Check if there is an extension name set
        String extensionName;
        try
        {
            extensionName = (String) props.getProperty("ExtensionName");
        }
        catch (Exception e)
        {
            extensionName = null;
        }
        if (extensionName == null || extensionName.isEmpty())
        {
            // No extension has been set - return reference to parent parent server object
            if (TypeChecker.instanceOf(serverObject, IWebRequestHandler.class))
            {
                return new IWebRequestHandlerProxy(serverObject);
            }
            else
            {
                return null;
            }
        }
        else
        {
            // Get the extension reference from cache if available
            if (extensionCache.containsKey(extensionName))
            {
                return new IWebRequestHandlerProxy(extensionCache.get(extensionName));
            }
            // This request is to be made on a specific extension
            // so we find the extension from the extension manager
            IServerObjectExtensionManager extnMgr = new IServerObjectExtensionManagerProxy(serverObject);
            IServerObjectExtension soe = extnMgr.findExtensionByTypeName(extensionName);
            if (TypeChecker.instanceOf(soe, IWebRequestHandler.class))
            {
                extensionCache.put(extensionName, soe);
                return new IWebRequestHandlerProxy(soe);
            }
            else
            {
                return null;
            }
        }
    }
}

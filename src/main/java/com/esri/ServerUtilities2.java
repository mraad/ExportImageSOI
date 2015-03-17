package com.esri;

import com.esri.arcgis.system.EnvironmentManager;
import com.esri.arcgis.system.IServerEnvironment2;
import com.esri.arcgis.system.IServerEnvironment2Proxy;
import com.esri.arcgis.system.UID;

import java.io.IOException;

/**
 */
public class ServerUtilities2
{
    public final static IServerEnvironment2 getServerEnvironment() throws IOException
    {
        final EnvironmentManager envMgr = new EnvironmentManager();
        final UID envUID = new UID();
        envUID.setValue("{32d4c328-e473-4615-922c-63c108f55e60}");
        return new IServerEnvironment2Proxy(envMgr.getEnvironment(envUID));
    }
}

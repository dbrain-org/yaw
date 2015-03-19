/*
 * Copyright [2015] [Eric Poitras]
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.dbrain.yaw.system.jetty;

import org.dbrain.yaw.directory.ServiceDirectory;
import org.dbrain.yaw.http.server.defs.CredentialsDef;
import org.dbrain.yaw.http.server.defs.FormLocationDef;
import org.dbrain.yaw.http.server.defs.ServletAppSecurityDef;
import org.dbrain.yaw.http.server.defs.ServletContextDef;
import org.dbrain.yaw.http.server.defs.ServletDef;
import org.dbrain.yaw.http.server.defs.ServletFilterDef;
import org.dbrain.yaw.http.server.defs.WebSocketDef;
import org.dbrain.yaw.system.app.SystemConfiguration;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.authentication.FormAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpSessionListener;
import javax.websocket.server.ServerContainer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: epoitras
 * Date: 15/07/13
 * Time: 10:45 PM
 * To change this template use File | Settings | File Templates.
 */
public class JettyServerBuilder {

    private final ServiceDirectory locator;

    public JettyServerBuilder( ServiceDirectory locator ) {
        this.locator = locator;
    }

    public void configureFilter( ServletContextHandler result, ServletFilterDef def ) {
        def.accept( new ServletFilterDef.Visitor() {

            @Override
            public void visit( ServletFilterDef.ServletFilterInstanceDef servletDef ) {
                result.addFilter( new FilterHolder( servletDef.getInstance() ),
                                  servletDef.getPathSpec(),
                                  EnumSet.of( DispatcherType.REQUEST ) );
            }

            @Override
            public void visit( ServletFilterDef.ServletFilterClassDef servletDef ) {
                Filter instance = locator.getJitInstance( servletDef.getFilterClass() );
                result.addFilter( new FilterHolder( instance ),
                                  servletDef.getPathSpec(),
                                  EnumSet.of( DispatcherType.REQUEST ) );
            }

        } );
    }

    public void configureServlet( ServletContextHandler result, ServletDef def ) {
        def.accept( new ServletDef.Visitor() {
            @Override
            public void visit( ServletDef.InstanceServletDef servletDef ) {
                ServletHolder servletHolder = new ServletHolder( servletDef.getInstance() );
                //servletHolder.setInitParameter(  );
                result.addServlet( servletHolder, servletDef.getPathSpec() );
            }
        } );
    }

    public FormAuthenticator createFormAuthenticator( FormLocationDef def ) {
        return new FormAuthenticator( def.getUrl(),
                                      def.getErrorURL(),
                                      false /* no, do not dispatch but redirect (302, 301?) to the error url */ );
    }

    public HashLoginService createHashLoginService( CredentialsDef def ) {
        return new HashLoginService( def.getRealm(), def.getFile() );
    }

    public ConstraintSecurityHandler createConstraintSecurityHandler( ServletAppSecurityDef def ) {

        ConstraintMapping cm = new ConstraintMapping();
        cm.setPathSpec( def.getPathSpec() );
        cm.setConstraint( ConstraintSecurityHandler.createConstraint( Constraint.__FORM_AUTH,
                                                                      true, /* yes, must authenticate */
                                                                      new String[]{
                                                                              def.getCredentialsDef().getSingleRole()
                                                                      },
                                                                      Constraint.DC_NONE ) );

        ConstraintSecurityHandler sh = new ConstraintSecurityHandler();
        sh.setRealmName( def.getCredentialsDef().getRealm() );
        sh.setAuthenticator( createFormAuthenticator( def.getFormLocationDef() ) );
        sh.setLoginService( createHashLoginService( def.getCredentialsDef() ) );
        sh.setConstraintMappings( Arrays.asList( cm ) );

        return sh;
    }

    public void configureWebSocket( ServerContainer serverContainer, WebSocketDef webSocketDef ) throws Exception {
        webSocketDef.accept( new WebSocketDef.Visitor() {
            @Override
            public void visit( WebSocketDef.EndpointClassWebSocketDef endpointClassWebSocketDef ) throws Exception {
                serverContainer.addEndpoint( endpointClassWebSocketDef.getEndpointClass() );
            }

            @Override
            public void visit( WebSocketDef.ServerEndpointConfigWebSocketDef serverEndpointConfig ) throws Exception {
                serverContainer.addEndpoint( serverEndpointConfig.getConfig() );
            }
        } );

    }


    public Handler configureServletContextHandler( Server server, ServletContextDef config ) {

        ServletContextHandler servletContextHandler = new ServletContextHandler( ( config.getSecurity() != null ) ? ServletContextHandler.SESSIONS : ServletContextHandler.NO_SESSIONS );
        servletContextHandler.setContextPath( config.getContextPath() );
        servletContextHandler.setServer( server );

        // Initialize javax.websocket layer
        if ( config.getWebSockets().size() > 0 ) {
            try {
                ServerContainer wscontainer = WebSocketServerContainerInitializer.configureContext(
                        servletContextHandler );

                for ( WebSocketDef wsd : config.getWebSockets() ) {
                    configureWebSocket( wscontainer, wsd );
                }

            } catch ( Exception e ) {
                throw new RuntimeException( e );
            }
        }

        if ( config.getSecurity() != null ) {
            // no url rewritting to put session id in the path
            // TODO kilantzis - put the line below somewhere else?
            servletContextHandler.setInitParameter( "org.eclipse.jetty.servlet.SessionIdPathParameterName", "none" );
            servletContextHandler.setSecurityHandler( createConstraintSecurityHandler( config.getSecurity() ) );
        }

        SessionHandler sessionHandler = new SessionHandler();
        servletContextHandler.setSessionHandler( sessionHandler );

        // Add system servlet context listener, if any.
        locator.listServices( ServletContextListener.class, SystemConfiguration.class )
               .forEach( servletContextHandler::addEventListener );

        // Add system session listener, if any.
        if ( sessionHandler != null ) {
            locator.listServices( HttpSessionListener.class, SystemConfiguration.class )
                   .forEach( sessionHandler::addEventListener );
        }


        // Add system configuration filters, if any.
        for ( ServletFilterDef filterDef : locator.listServices( ServletFilterDef.class, SystemConfiguration.class ) ) {
            configureFilter( servletContextHandler, filterDef );
        }


        // Add user filters, if any.
        for ( ServletFilterDef filterDef : config.getFilters() ) {
            configureFilter( servletContextHandler, filterDef );
        }

        for ( ServletDef servletDef : config.getServlets() ) {
            configureServlet( servletContextHandler, servletDef );
        }

        return servletContextHandler;
    }

    public Handler configureServletContextsHandler( Server server, List<ServletContextDef> defs ) {
        if ( defs == null || defs.size() == 0 ) {
            return null;
        } else if ( defs.size() == 1 ) {
            return configureServletContextHandler( server, defs.get( 0 ) );
        } else {
            HandlerList result = new HandlerList();
            result.setServer( server );
            for ( ServletContextDef def : defs ) {
                result.addHandler( configureServletContextHandler( server, def ) );
            }
            return result;
        }
    }


}

package com.gallery.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Closes the connection pool when Tomcat undeploys the application. */
@WebListener
public class DbLifecycleListener implements ServletContextListener {

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        DbUtil.close();
    }
}

/*
 * Copyright (c) 2015 HolidayCheck AG.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.holidaycheck.marathon.maven;

import static com.holidaycheck.marathon.maven.Utils.readApp;
import static com.holidaycheck.marathon.maven.Utils.readGroup;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import feign.Feign;
import feign.RequestInterceptor;
import feign.auth.BasicAuthRequestInterceptor;

import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.MarathonClient;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.model.v2.Group;
import mesosphere.marathon.client.utils.MarathonException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Deploys via Marathon by sending config.
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY)
public class DeployMojo extends AbstractMarathonMojo {

    /**
     * URL of the marathon host as specified in pom.xml.
     */
    @Parameter(property = "marathonHost", required = true)
    private String marathonHost;

    /**
     * Flag which indicate if we are deploying an application (the default)
     * or a group.
     */
    @Parameter(property = "group", required = false)
    private boolean group;

    /**
     * Flag which indicate if we must destroy the application or group before
     * deploying.
     */
    @Parameter(property = "deleteBeforeDeploy", required = false)
    private boolean deleteBeforeDeploy;

    /**
     * Don't check the certificate if using https.
     */
    @Parameter(property = "insecure", required = false)
    private boolean insecure;
    
    @Parameter(property = "login", required = false)
    private String login;
    
    @Parameter(property = "password", required = false)
    private String password;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final Marathon marathon;
        if (login == null && password == null) {
            marathon = MarathonClient.getInstance(marathonHost);
        } else {
            Feign.Builder builder = new Feign.Builder();
            RequestInterceptor authInterceptor = new BasicAuthRequestInterceptor(login, password);
            builder.requestInterceptor(authInterceptor);
            marathon = MarathonClient.getInstance(builder, marathonHost);
        }
        if (insecure) {
            disableSslVerification();
        }
        if (!group) {
            final App app = readApp(finalMarathonConfigFile);
            getLog().info("deploying Marathon config for " + app.getId()
                    + " from " + finalMarathonConfigFile + " to " + marathonHost);
            if (appExists(marathon, app.getId()) && !this.deleteBeforeDeploy) {
                getLog().info(app.getId() + " already exists - will be updated");
                updateApp(marathon, app);
            } else {
                if (deleteBeforeDeploy) {
                    try {
                        marathon.deleteApp(app.getId());
                        getLog().info(app.getId() + " application deleted");
                    } catch (MarathonException e) {
                        getLog().error("An error as occured while deleting "
                            + "application '" + app.getId() + "': " + e.getMessage(), e);
                    }
                }
                getLog().info(app.getId() + " does not exist yet - will be created");
                createApp(marathon, app);
            }
        } else {
            final Group group = readGroup(finalMarathonConfigFile);
            getLog().info("deploying Marathon config for group " + group.getId()
                    + " from " + finalMarathonConfigFile + " to " + marathonHost);
            if (groupExists(marathon, group.getId()) && !this.deleteBeforeDeploy) {
                getLog().info(group.getId() + " group already exists - will be updated");
                updateGroup(marathon, group);
            } else {
                if (deleteBeforeDeploy) {
                    try {
                        marathon.deleteGroup(group.getId());
                        getLog().info(group.getId() + " group deleted");
                    } catch (MarathonException e) {
                        getLog().error("An error as occured while deleting "
                            + "group '" + group.getId() + "': " + e.getMessage(), e);
                    }
                }
                getLog().info(group.getId() + " group does not exist yet - will be created");
                createGroup(marathon, group);
            }
        }
    }

    private void updateApp(Marathon marathon, App app) throws MojoExecutionException {
        try {
            marathon.updateApp(app.getId(), app, true);
        } catch (Exception updateAppException) {
            throw new MojoExecutionException("Failed to update Marathon config file at "
                    + marathonHost, updateAppException);
        }
    }

    private void createApp(Marathon marathon, App app) throws MojoExecutionException {
        try {
            marathon.createApp(app);
        } catch (Exception createAppException) {
            throw new MojoExecutionException("Failed to push Marathon config file to "
                    + marathonHost, createAppException);
        }
    }

    private boolean groupExists(Marathon marathon, String groupId) throws MojoExecutionException {
        try {
            marathon.getGroup(groupId);
            return true;
        } catch (MarathonException getAppException) {
            if (getAppException.getMessage().contains("404")) {
                return false;
            } else {
                throw new MojoExecutionException("Failed to check if an app " + groupId + "exists",
                        getAppException);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to check if an app " + groupId + "exists", e);
        }
    }

    private void updateGroup(Marathon marathon, Group appGroup) throws MojoExecutionException {
        try {
            for (Group g : appGroup.getGroups()) {
                for (App a : g.getApps()) {
                    marathon.updateApp(a.getId(), a, true);
                }
            }
        } catch (Exception updateAppException) {
            throw new MojoExecutionException("Failed to update Marathon config file at "
                    + marathonHost, updateAppException);
        }
    }

    private void createGroup(Marathon marathon, Group appGroup) throws MojoExecutionException {
        try {
            marathon.createGroup(appGroup);
        } catch (Exception createAppException) {
            throw new MojoExecutionException("Failed to push Marathon config file to "
                    + marathonHost, createAppException);
        }
    }

    private static void disableSslVerification() {
        try {
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                }
                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            }
            };

            // Install the all-trusting trust manager
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };

            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
    }
}

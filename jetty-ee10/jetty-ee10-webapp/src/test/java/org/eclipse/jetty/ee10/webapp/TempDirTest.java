//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.ee10.webapp;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import jakarta.servlet.ServletContext;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.toolchain.test.FS;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.toolchain.test.PathMatchers;
import org.eclipse.jetty.util.resource.FileSystemPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

public class TempDirTest
{
    private Server server;
    private WebAppContext webapp;

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    private Path jettyBase;

    @BeforeEach
    public void before()
    {
        assertThat(FileSystemPool.INSTANCE.mounts(), empty());
        FS.ensureEmpty(jettyBase);
        System.setProperty("jetty.base", jettyBase.toString());
    }

    public void setupServer()
    {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        server.addConnector(connector);

        File testWebAppDir = MavenTestingUtils.getProjectDir("src/test/webapp");
        webapp = new WebAppContext();
        webapp.setContextPath("/");
        webapp.setWar(testWebAppDir.getAbsolutePath());
        server.setHandler(webapp);
    }

    @AfterEach
    public void stopServer() throws Exception
    {
        if (server != null)
            server.stop();
        assertThat(FileSystemPool.INSTANCE.mounts(), empty());
    }

    @AfterEach
    public void tearDown()
    {
        assertThat(FileSystemPool.INSTANCE.mounts(), empty());
    }

    /**
     * Test ServletContext.TEMPDIR as valid directory with types File, String and Path.
     */
    @ParameterizedTest
    @ValueSource(strings = {"File", "String", "Path"})
    public void attributeWithValidDirectory(String type, @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path tmpDir) throws Exception
    {
        WebAppContext webAppContext = new WebAppContext();
        FS.ensureDirExists(tmpDir);
        switch (type)
        {
            case "File" -> webAppContext.setAttribute(ServletContext.TEMPDIR, tmpDir.toFile());
            case "String" -> webAppContext.setAttribute(ServletContext.TEMPDIR, tmpDir.toString());
            case "Path" -> webAppContext.setAttribute(ServletContext.TEMPDIR, tmpDir);
            default -> throw new IllegalStateException();
        }

        // Test we have correct value as the webapp temp directory.
        WebInfConfiguration webInfConfiguration = new WebInfConfiguration();
        webInfConfiguration.resolveTempDirectory(webAppContext);
        Path webappTempDir = webAppContext.getTempDirectory().toPath();
        assertThat(tmpDir, PathMatchers.isSame(webappTempDir));
    }

    /**
     * ServletContext.TEMPDIR as File to a non existent directory.
     */
    @ParameterizedTest
    @ValueSource(strings = {"File", "String", "Path"})
    public void attributeWithNonExistentDirectory(String type, @TempDir(cleanup = CleanupMode.ON_SUCCESS) Path path) throws Exception
    {
        Server server = new Server();
        WebAppContext webAppContext = new WebAppContext();
        server.setHandler(webAppContext);
        Path tmpDir = path.resolve("foo_did_not_exist");
        assertThat(Files.exists(tmpDir), is(false));

        switch (type)
        {
            case "File" -> webAppContext.setAttribute(ServletContext.TEMPDIR, tmpDir.toFile());
            case "String" -> webAppContext.setAttribute(ServletContext.TEMPDIR, tmpDir.toString());
            case "Path" -> webAppContext.setAttribute(ServletContext.TEMPDIR, tmpDir);
            default -> throw new IllegalStateException();
        }

        // Test we have correct value as the webapp temp directory.
        WebInfConfiguration webInfConfiguration = new WebInfConfiguration();
        webInfConfiguration.resolveTempDirectory(webAppContext);
        Path webappTmpDir = webAppContext.getTempDirectory().toPath();
        assertThat(webappTmpDir, PathMatchers.isSame(tmpDir));
    }

    /**
     * Test Server.setTempDirectory as valid directory
     */
    @Test
    public void serverTempDirAttributeWithValidDirectory(@TempDir(cleanup = CleanupMode.ON_SUCCESS) Path tmpDir) throws Exception
    {
        WebAppContext webAppContext = new WebAppContext();
        Server server = new Server();
        webAppContext.setServer(server);
        FS.ensureDirExists(tmpDir);
        server.setTempDirectory(tmpDir.toFile());

        // Test we have correct value as the webapp temp directory.
        WebInfConfiguration webInfConfiguration = new WebInfConfiguration();
        webInfConfiguration.resolveTempDirectory(webAppContext);
        File tempDirectory = webAppContext.getTempDirectory();
        assertThat(tempDirectory.exists(), is(true));
        assertThat(tempDirectory.getParentFile().toPath(), PathMatchers.isSame(tmpDir));
    }

    /**
     * <code>${jetty.base}</code> directory exists and has a subdirectory called work
     * so webappContent#tempDirectory is created under <code>java.io.tmpdir</code>
     */
    @Test
    public void jettyBaseWorkExists() throws Exception
    {
        Path workDir = jettyBase.resolve("work");
        FS.ensureDirExists(workDir);
        WebInfConfiguration webInfConfiguration = new WebInfConfiguration();
        Server server = new Server();
        WebAppContext webAppContext = new WebAppContext();
        server.setHandler(webAppContext);
        webInfConfiguration.resolveTempDirectory(webAppContext);
        assertThat(webAppContext.getTempDirectory().getParentFile().toPath(), PathMatchers.isSame(workDir));
    }
}

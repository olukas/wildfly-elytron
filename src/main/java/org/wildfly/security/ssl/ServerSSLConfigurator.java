/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.security.ssl;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ServerSSLConfigurator implements SSLConfigurator {

    private final ProtocolSelector protocolSelector;
    private final CipherSuiteSelector cipherSuiteSelector;
    private final boolean wantClientAuth;
    private final boolean needClientAuth;

    ServerSSLConfigurator(final ProtocolSelector protocolSelector, final CipherSuiteSelector cipherSuiteSelector, final boolean wantClientAuth, final boolean needClientAuth) {
        this.protocolSelector = protocolSelector;
        this.cipherSuiteSelector = cipherSuiteSelector;
        this.wantClientAuth = wantClientAuth;
        this.needClientAuth = needClientAuth;
    }

    private void configure(SSLParameters params, String[] supportedProtocols, String[] supportedCipherSuites) {
        params.setProtocols(protocolSelector.evaluate(supportedProtocols));
        params.setCipherSuites(cipherSuiteSelector.evaluate(supportedCipherSuites));
        params.setUseCipherSuitesOrder(true);
        params.setNeedClientAuth(needClientAuth);
        params.setWantClientAuth(wantClientAuth);
    }

    public void configure(final SSLContext context, final SSLServerSocket sslServerSocket) {
        sslServerSocket.setUseClientMode(false);
        final SSLParameters sslParameters = sslServerSocket.getSSLParameters();
        configure(sslParameters, sslServerSocket.getSupportedProtocols(), sslServerSocket.getSupportedCipherSuites());
        sslServerSocket.setSSLParameters(sslParameters);
    }

    public void configure(final SSLContext context, final SSLSocket sslSocket) {
        sslSocket.setUseClientMode(false);
        final SSLParameters sslParameters = sslSocket.getSSLParameters();
        configure(sslParameters, sslSocket.getSupportedProtocols(), sslSocket.getSupportedCipherSuites());
        sslSocket.setSSLParameters(sslParameters);
    }

    public void configure(final SSLContext context, final SSLEngine sslEngine) {
        sslEngine.setUseClientMode(false);
        final SSLParameters sslParameters = sslEngine.getSSLParameters();
        configure(sslParameters, sslEngine.getSupportedProtocols(), sslEngine.getSupportedCipherSuites());
        sslEngine.setSSLParameters(sslParameters);
    }

    public SSLParameters getDefaultSSLParameters(final SSLContext sslContext, final SSLParameters original) {
        final SSLParameters supportedSSLParameters = sslContext.getSupportedSSLParameters();
        configure(original, supportedSSLParameters.getProtocols(), supportedSSLParameters.getCipherSuites());
        return original;
    }

    public SSLParameters getSupportedSSLParameters(final SSLContext sslContext, final SSLParameters original) {
        return getDefaultSSLParameters(sslContext, original);
    }

    public void setWantClientAuth(final SSLContext context, final SSLSocket sslSocket, final boolean value) {
        // ignored
    }

    public void setWantClientAuth(final SSLContext context, final SSLEngine sslEngine, final boolean value) {
        // ignored
    }

    public void setWantClientAuth(final SSLContext sslContext, final SSLServerSocket sslServerSocket, final boolean value) {
        // ignored
    }

    public void setNeedClientAuth(final SSLContext context, final SSLSocket sslSocket, final boolean value) {
        // ignored
    }

    public void setNeedClientAuth(final SSLContext context, final SSLEngine sslEngine, final boolean value) {
        // ignored
    }

    public void setNeedClientAuth(final SSLContext sslContext, final SSLServerSocket sslServerSocket, final boolean value) {
        // ignored
    }

    public void setEnabledCipherSuites(final SSLContext sslContext, final SSLSocket sslSocket, final String[] cipherSuites) {
        // ignored
    }

    public void setEnabledCipherSuites(final SSLContext sslContext, final SSLEngine sslEngine, final String[] cipherSuites) {
        // ignored
    }

    public void setEnabledCipherSuites(final SSLContext sslContext, final SSLServerSocket sslServerSocket, final String[] suites) {
        // ignored
    }

    public void setEnabledProtocols(final SSLContext sslContext, final SSLSocket sslSocket, final String[] protocols) {
        // ignored
    }

    public void setEnabledProtocols(final SSLContext sslContext, final SSLEngine sslEngine, final String[] protocols) {
        // ignored
    }

    public void setEnabledProtocols(final SSLContext sslContext, final SSLServerSocket sslServerSocket, final String[] protocols) {
        // ignored
    }

    private SSLParameters redefine(SSLParameters original, String[] supportedCipherSuites, String[] supportedProtocols) {
        final SSLParameters params = new SSLParameters();
        configure(params, protocolSelector.evaluate(supportedProtocols), cipherSuiteSelector.evaluate(supportedCipherSuites));
        // copy all other parameters over
        params.setServerNames(original.getServerNames());
        params.setSNIMatchers(original.getSNIMatchers());
        params.setAlgorithmConstraints(original.getAlgorithmConstraints());
        params.setEndpointIdentificationAlgorithm(original.getEndpointIdentificationAlgorithm());
        return params;
    }

    public void setSSLParameters(final SSLContext sslContext, final SSLSocket sslSocket, final SSLParameters parameters) {
        sslSocket.setSSLParameters(redefine(parameters, sslSocket.getSupportedCipherSuites(), sslSocket.getSupportedProtocols()));
    }

    public void setSSLParameters(final SSLContext sslContext, final SSLEngine sslEngine, final SSLParameters parameters) {
        sslEngine.setSSLParameters(redefine(parameters, sslEngine.getSupportedCipherSuites(), sslEngine.getSupportedProtocols()));
    }

    public void setSSLParameters(final SSLContext sslContext, final SSLServerSocket sslServerSocket, final SSLParameters parameters) {
        sslServerSocket.setSSLParameters(redefine(parameters, sslServerSocket.getSupportedCipherSuites(), sslServerSocket.getSupportedProtocols()));
    }

    public void setUseClientMode(final SSLContext sslContext, final SSLSocket sslSocket, final boolean mode) {
        // ignored
    }

    public void setUseClientMode(final SSLContext sslContext, final SSLEngine sslEngine, final boolean mode) {
        // ignored
    }

    public void setUseClientMode(final SSLContext sslContext, final SSLServerSocket sslServerSocket, final boolean mode) {
        // ignored
    }
}

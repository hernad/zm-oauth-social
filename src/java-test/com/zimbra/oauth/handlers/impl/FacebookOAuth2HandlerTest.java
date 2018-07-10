/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra OAuth Social Extension
 * Copyright (C) 2018 Synacor, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.oauth.handlers.impl;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.matches;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.easymock.PowerMock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import com.fasterxml.jackson.databind.JsonNode;
import com.zimbra.client.ZMailbox;
import com.zimbra.cs.account.Account;
import com.zimbra.oauth.handlers.impl.FacebookOAuth2Handler.FacebookOAuth2Constants;
import com.zimbra.oauth.models.OAuthInfo;
import com.zimbra.oauth.utilities.Configuration;
import com.zimbra.oauth.utilities.OAuth2ConfigConstants;
import com.zimbra.oauth.utilities.OAuth2Constants;
import com.zimbra.oauth.utilities.OAuth2DataSource;
import com.zimbra.oauth.utilities.OAuth2HttpConstants;

/**
 * Test class for {@link FacebookOAuth2Handler}.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ OAuth2DataSource.class, OAuth2Handler.class, FacebookOAuth2Handler.class, ZMailbox.class })
@SuppressStaticInitializationFor("com.zimbra.client.ZMailbox")
public class FacebookOAuth2HandlerTest {

    /**
     * Class under test.
     */
    protected FacebookOAuth2Handler handler;

    /**
     * Mock configuration handler property.
     */
    protected Configuration mockConfig = EasyMock.createMock(Configuration.class);

    /**
     * Mock data source handler property.
     */
    protected OAuth2DataSource mockDataSource = EasyMock.createMock(OAuth2DataSource.class);

    /**
     * ClientId for testing.
     */
    protected final String clientId = "test-client";

    /**
     * ClientSecret for testing.
     */
    protected final String clientSecret = "test-secret";

    /**
     * Hostname for testing.
     */
    protected final String hostname = "localhost";

    /**
     * Redirect URI for testing.
     */
    protected final String clientRedirectUri = "http://localhost/oauth2/authenticate";

    /**
     * Setup for tests.
     *
     * @throws Exception If there are issues mocking
     */
    @Before
    public void setUp() throws Exception {
        handler = PowerMock.createPartialMockForAllMethodsExcept(FacebookOAuth2Handler.class,
            "authorize", "authenticate");
        Whitebox.setInternalState(handler, "config", mockConfig);
        Whitebox.setInternalState(handler, "relayKey", FacebookOAuth2Constants.RELAY_KEY.getValue());
        Whitebox.setInternalState(handler, "typeKey",
            OAuth2HttpConstants.OAUTH2_TYPE_KEY.getValue());
        Whitebox.setInternalState(handler, "authenticateUri",
            FacebookOAuth2Constants.AUTHENTICATE_URI.getValue());
        Whitebox.setInternalState(handler, "authorizeUriTemplate",
            FacebookOAuth2Constants.AUTHORIZE_URI_TEMPLATE.getValue());
        Whitebox.setInternalState(handler, "requiredScopes",
            FacebookOAuth2Constants.REQUIRED_SCOPES.getValue());
        Whitebox.setInternalState(handler, "scopeDelimiter",
            FacebookOAuth2Constants.SCOPE_DELIMITER.getValue());
        Whitebox.setInternalState(handler, "client", FacebookOAuth2Constants.CLIENT_NAME.getValue());
        Whitebox.setInternalState(handler, "dataSource", mockDataSource);
    }

    /**
     * Test method for {@link FacebookOAuth2Handler#FacebookOAuth2Handler}<br>
     * Validates that the constructor configured some necessary properties.
     *
     * @throws Exception If there are issues testing
     */
    @Test
    public void testFacebookOAuth2Handler() throws Exception {
        final OAuth2DataSource mockDataSource = EasyMock.createMock(OAuth2DataSource.class);

        expect(mockConfig.getString(OAuth2ConfigConstants.LC_HOST_URI_TEMPLATE.getValue(),
            OAuth2Constants.DEFAULT_HOST_URI_TEMPLATE.getValue()))
                .andReturn(OAuth2Constants.DEFAULT_HOST_URI_TEMPLATE.getValue());
        expect(mockConfig.getString(OAuth2ConfigConstants.LC_ZIMBRA_SERVER_HOSTNAME.getValue()))
            .andReturn(hostname);
        PowerMock.mockStatic(OAuth2DataSource.class);
        expect(OAuth2DataSource.createDataSource(FacebookOAuth2Constants.CLIENT_NAME.getValue(),
            FacebookOAuth2Constants.HOST_FACEBOOK.getValue())).andReturn(mockDataSource);

        replay(mockConfig);
        PowerMock.replay(OAuth2DataSource.class);

        new FacebookOAuth2Handler(mockConfig);

        verify(mockConfig);
        PowerMock.verify(OAuth2DataSource.class);
    }

    /**
     * Test method for {@link FacebookOAuth2Handler#authorize}<br>
     * Validates that the authorize method returns a location with an encoded
     * redirect uri.
     *
     * @throws Exception If there are issues testing
     */
    @Test
    public void testAuthorize() throws Exception {
        final String encodedUri = URLEncoder.encode(clientRedirectUri,
            OAuth2Constants.ENCODING.getValue());
        // use contact type
        final Map<String, String> params = new HashMap<String, String>();
        params.put(OAuth2HttpConstants.OAUTH2_TYPE_KEY.getValue(), "contact");
        final String authorizeBase = String.format(
            FacebookOAuth2Constants.AUTHORIZE_URI_TEMPLATE.getValue(), clientId, encodedUri, "code",
            FacebookOAuth2Constants.REQUIRED_SCOPES.getValue());
        // expect a contact state with no relay
        final String expectedAuthorize = authorizeBase + "&state=;contact";

        // expect buildAuthorize call
        expect(handler.buildAuthorizeUri(FacebookOAuth2Constants.AUTHORIZE_URI_TEMPLATE.getValue(),
            null, "contact")).andReturn(authorizeBase);

        replay(handler);

        final String authorizeLocation = handler.authorize(params, null);

        // verify build was called
        verify(handler);

        assertNotNull(authorizeLocation);
        assertEquals(expectedAuthorize, authorizeLocation);
    }

    /**
     * Test method for {@link FacebookOAuth2Handler#authenticate}<br>
     * Validates that the authenticate method calls sync datasource.
     *
     * @throws Exception If there are issues testing
     */
    @Test
    public void testAuthenticate() throws Exception {
        final String user_id = "1234567890";
        final String accessToken = "access-token";
        final String zmAuthToken = "zm-auth-token";
        final OAuthInfo mockOAuthInfo = EasyMock.createMock(OAuthInfo.class);
        final ZMailbox mockZMailbox = EasyMock.createMock(ZMailbox.class);
        final JsonNode mockCredentials = EasyMock.createMock(JsonNode.class);
        final JsonNode mockCredentialsAToken = EasyMock.createMock(JsonNode.class);

        PowerMock.mockStatic(OAuth2Handler.class);

        expect(mockOAuthInfo.getAccount()).andReturn(null);
        expect(mockConfig.getString(
            matches(String.format(OAuth2ConfigConstants.LC_OAUTH_CLIENT_ID_TEMPLATE.getValue(),
                FacebookOAuth2Constants.CLIENT_NAME.getValue())),
            matches(FacebookOAuth2Constants.CLIENT_NAME.getValue()), anyObject())).andReturn(clientId);
        expect(mockConfig.getString(
            matches(String.format(OAuth2ConfigConstants.LC_OAUTH_CLIENT_SECRET_TEMPLATE.getValue(),
                FacebookOAuth2Constants.CLIENT_NAME.getValue())),
            matches(FacebookOAuth2Constants.CLIENT_NAME.getValue()), anyObject()))
                .andReturn(clientSecret);
        expect(mockConfig.getString(
            matches(String.format(OAuth2ConfigConstants.LC_OAUTH_CLIENT_REDIRECT_URI_TEMPLATE.getValue(),
                FacebookOAuth2Constants.CLIENT_NAME.getValue())),
            matches(FacebookOAuth2Constants.CLIENT_NAME.getValue()), anyObject()))
                .andReturn(clientRedirectUri);
        expect(handler.getZimbraMailbox(anyObject(String.class))).andReturn(mockZMailbox);
        expect(OAuth2Handler.getTokenRequest(anyObject(OAuthInfo.class), anyObject(String.class)))
            .andReturn(mockCredentials);
        handler.validateTokenResponse(anyObject(JsonNode.class));
        EasyMock.expectLastCall().once();
        expect(mockCredentials.get("access_token")).andReturn(mockCredentialsAToken);
        expect(mockCredentialsAToken.asText()).andReturn(accessToken);
        expect(handler.getPrimaryEmail(anyObject(JsonNode.class), anyObject(Account.class)))
            .andReturn(user_id);

        mockOAuthInfo.setClientId(matches(clientId));
        EasyMock.expectLastCall().once();
        mockOAuthInfo.setClientSecret(matches(clientSecret));
        EasyMock.expectLastCall().once();
        mockOAuthInfo.setClientRedirectUri(matches(clientRedirectUri));
        EasyMock.expectLastCall().once();
        mockOAuthInfo.setTokenUrl(matches(FacebookOAuth2Constants.AUTHENTICATE_URI.getValue()));
        EasyMock.expectLastCall().once();
        expect(mockOAuthInfo.getZmAuthToken()).andReturn(zmAuthToken);
        mockOAuthInfo.setUsername(user_id);
        EasyMock.expectLastCall().once();
        mockOAuthInfo.setRefreshToken(accessToken);
        EasyMock.expectLastCall().once();
        mockDataSource.syncDatasource(mockZMailbox, mockOAuthInfo, null);
        EasyMock.expectLastCall().once();

        replay(handler);
        PowerMock.replay(OAuth2Handler.class);
        replay(mockOAuthInfo);
        replay(mockConfig);
        replay(mockCredentials);
        replay(mockCredentialsAToken);
        replay(mockDataSource);

        handler.authenticate(mockOAuthInfo);

        verify(handler);
        PowerMock.verify(OAuth2Handler.class);
        verify(mockOAuthInfo);
        verify(mockConfig);
        verify(mockCredentials);
        verify(mockCredentialsAToken);
        verify(mockDataSource);
    }

}

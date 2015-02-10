/*
 * Copyright 2014-15 Intelix Pty Ltd
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

EventCommAuthAccessPermissionsChanged = "EventCommAuthAccessPermissionsChanged";
EventCommAuthAccessAllowed = "EventCommAuthAccessAllowed";
EventCommAuthAccessDenied = "EventCommAuthAccessDenied";

EventCommAuthLoginPending = "EventCommAuthLoginPending";
EventCommAuthLoginRequired = "EventCommAuthLoginRequired";

EventCommAuthLoginRequest = "EventCommAuthLoginRequest";


define(['logging', 'eventing', 'cookies', 'comm_protocol'], function (logging, eventing, cookies, protocol) {

    
    var componentId = "comm/Auth";
    var securityCookie = "_eventstreams_session_token";
    var defaultExpiryDays = 30;
    
    var token = cookies.readCookie(securityCookie);
    var validToken = false;
    
    function updateToken(newToken) {
        token = newToken;
        validToken = newToken;
        cookies.createCookie(securityCookie, newToken, defaultExpiryDays);
    }
    
    function onAuthorizationMessage(e) {
        var payload = JSON.parse(e.detail);
        var allowAccess = payload.allow;
        if (allowAccess) {
            updateToken(payload.token);
            if (logging.isInfo()) {
                logging.logInfo(componentId, "Access granted, security token: " + payload.token);
            }
            eventing.raiseEvent(EventCommAuthAccessPermissionsChanged, payload.permissions);
            eventing.raiseEvent(EventCommAuthAccessAllowed);
        } else {
            updateToken(false);
            if (logging.isInfo()) {
                logging.logInfo(componentId, "Access denied");
            }
            eventing.raiseEvent(EventCommAuthAccessDenied);
        }
    }
    eventing.addEventListener(EventCommProtoInboundAuthorization, onAuthorizationMessage);

    
    function reloginWithToken() {
        validToken = false;
        if (token) {
            if (logging.isInfo()) {
                logging.logInfo(componentId, "Authenticating with token: " + token);
            }
            eventing.raiseEvent(EventCommAuthLoginPending);
            protocol.sendTokenAuth(token);
        } else {
            if (logging.isInfo()) {
                logging.logInfo(componentId, "Login required");
            }
            eventing.raiseEvent(EventCommAuthLoginRequired);
        }
    }
    eventing.addEventListener(EventCommProtoHandshakeCompleted, reloginWithToken);

    
    function authWithCredentials(e) {
        updateToken(false);
        var user = e.detail.user;
        var passwordHash = e.detail.passwordHash;
        if (logging.isInfo()) {
            logging.logInfo(componentId, "Authenticating with credentials. User: " + user + ", hash: " + passwordHash);
        }
        if (!user || !passwordHash) {
            eventing.raiseEvent(EventCommAuthAccessDenied);
        } else {
            eventing.raiseEvent(EventCommAuthLoginPending);
            protocol.sendCredentialsAuth(user, passwordHash);
        }
    }
    eventing.addEventListener(EventCommAuthLoginRequest, authWithCredentials);
    
    return {
        isAuthenticated: function() { return validToken; }
    };

});
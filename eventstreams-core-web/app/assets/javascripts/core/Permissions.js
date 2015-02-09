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

EventSecurityPermissionsUpdated = "EventSecurityPermissionsUpdated";

define(['logging', 'eventing', 'comm_auth'], function (logging, eventing, auth) {

    
    var componentId = "security/Permissions";

    var allowedDomains = [];
    var permissionSets = [];


    function updatePermissions(e) {
        
        var newPermissions = e.detail ? e.detail : [];
        
        var newAllowedDomains = [];
        var newPermissionSets = [];

        if (logging.isDebug()) {
            logging.logInfo(componentId, "New permission set: " + JSON.stringify(newPermissions));
        } else if (logging.isInfo()) {
            logging.logInfo(componentId, "New permission set");
        }

        newPermissions.forEach(function(rolePerm) {
            var domainPerms = rolePerm.p;

            if (domainPerms && $.isArray(domainPerms) && domainPerms.length > 0) {
                domainPerms.forEach(function(domainPerm) {
                    var nextDom = domainPerm.d;

                    if (nextDom && nextDom.id) {

                        var nextSet = domainPerm.p && $.isArray(domainPerm.p) ? domainPerm.p : [];

                        if ((nextSet.length > 0) && $.inArray(nextDom.id, newAllowedDomains) < 0) {
                            newAllowedDomains.push(nextDom.id);
                        }

                        nextSet.forEach(function(nextPerm) {
                            if (nextPerm && nextPerm.t && $.inArray(nextPerm.t, newPermissionSets) < 0) {
                                newPermissionSets.push(nextPerm.t);
                            }
                        });
                    }
                });
            }

        });

        permissionSets = newPermissionSets.map(function(next) { return new RegExp(next);});
        allowedDomains = newAllowedDomains;

        if (logging.isInfo()) {
            logging.logInfo(componentId, "Enabled permissions: " + newPermissionSets );
            logging.logInfo(componentId, "Enabled domains: " + newAllowedDomains);
        }
        eventing.raiseEvent(EventSecurityPermissionsUpdated);
    }
    eventing.addEventListener(EventCommAuthAccessPermissionsChanged, updatePermissions);


    return {
        
        hasDomainPermission: function(domain) {
          return $.inArray("*", allowedDomains) > -1 || $.inArray(domain, allowedDomains) > -1;
        },
        
        hasTopicPermission: function(component, topic) {
            var combined = component + "#" + topic;
            return permissionSets.some(function(next) {
                return next.test(combined);
            });
        }

    };

});
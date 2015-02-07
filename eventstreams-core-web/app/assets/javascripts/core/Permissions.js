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

define(['logging'], function (logging) {


    var allowedDomains = [];
    var permissionSets = [];


    return {
    
        updatePermissions: function(newPermissions) {
            var newAllowedDomains = [];
            var newPermissionSets = [];
            allDomainsAllowed = false;

            if (logging.isDebug()) {
                logging.logInfo("auth", "New permission set: " + JSON.stringify(newPermissions));
            } else if (logging.isInfo()) {
                logging.logInfo("auth", "New permission set");
            }

            newPermissions.forEach(function(rolePerm) {
                var allowSelected = rolePerm.a;
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
                logging.logInfo("auth", "Enabled permissions: " + newPermissionSets );
                logging.logInfo("auth", "Enabled domains: " + newAllowedDomains);
            }

        },
        
        hasDomainPermission: function(domain) {
          return $.inArray("*", allowedDomains) > -1 || $.inArray(domain, allowedDomains) > -1;
        },
        
        hasTopicPermission: function(component, topic) {
            var combined = component + "#" + topic;
            return permissionSets.some(function(next) {
                var hasMatch = next.set.some(function(nextPattern) {
                    return nextPattern.test(combined);
                });                
                return hasMatch == next.allowSelected;
            });
        }

    };

});
/*
 * Copyright 2014 Intelix Pty Ltd
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

(function() {

    function adminApp(path) {
        return "admin/app/" + path;
    }

    require.config({

        /*noinspection */
        paths: {
            common: "../lib/common/javascripts",
            jquery: "../lib/jquery/jquery",
            react: "../lib/react/react-with-addons",
            bootstrap: "../lib/bootstrap/js/bootstrap",
            toastr: "../lib/toastr/toastr",


            app_layout: adminApp("Layout"),

            app_navbar: adminApp("navigation/Navbar"),
            app_navbar_el_mixin: adminApp("navigation/NavbarElementMixin"),
            app_navbar_gates: adminApp("navigation/NavbarGates"),
            app_navbar_flows: adminApp("navigation/NavbarFlows"),
            app_navbar_agents: adminApp("navigation/NavbarAgents"),
            app_navbar_notif: adminApp("navigation/NavbarNotifications"),

            app_content: adminApp("content/ContentManager"),
            app_content_nodetabs: adminApp("content/commons/ClusterNodesTabs"),

            app_gates: adminApp("content/gates/Content"),
            app_gates_table: adminApp("content/gates/GatesTable"),
            app_gates_table_row: adminApp("content/gates/GatesTableRow"),

            app_flows: adminApp("content/flows/Content"),
            app_agents: adminApp("content/agents/Content"),
            app_notif: adminApp("content/notif/Content"),




            lz: "/assets/javascripts/lz-string",
            coreMixin: "tools/CoreMixin",
            subscriberMixin: "tools/AutoSubscribeMixin",
            sendOnlyMixin: "tools/SendOnlyMixin",
            wsclient: "tools/ServerClient"
        },
        shim: {
            bootstrap: {
                deps: ["jquery"]
            },
            toastr: {
                deps: ["jquery"]
            },
            jquery: {
                exports: "$"
            }
        }
    });

    require(["admin/app"]);

})();
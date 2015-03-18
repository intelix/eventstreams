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

(function() {


    require.config({

        /*noinspection */
        paths: {
            common: "../lib/common/javascripts",
            jquery: "../lib/jquery/jquery",
            crypto_core: "../lib/cryptojs/components/core-min",
            crypto_sha256: "../lib/cryptojs/components/sha256-min",
            react: "../lib/react/react-with-addons",
            bootstrap: "../lib/bootstrap/js/bootstrap",
            toastr: "../lib/toastr/toastr",
            lz: "/assets/javascripts/lz-string",
            packery: "/assets/javascripts/packery.pkgd.min",

            logging: "/assets/javascripts/core/Logging",
            eventing: "/assets/javascripts/core/Eventing",
            cookies: "/assets/javascripts/core/Cookies",
            permissions: "/assets/javascripts/core/Permissions",
            comm_connectivity: "/assets/javascripts/core/CommConnectivity",
            comm_protocol: "/assets/javascripts/core/CommProtocol",
            comm_auth: "/assets/javascripts/core/CommAuth",
            comm_subscriptions: "/assets/javascripts/core/CommSubscriptions",


            common_nodetabs: ("commons/ClusterNodesTabs"),
            common_editor_mixin: ("commons/JSONEditorModalMixin"),
            common_link_edit: ("commons/EditLink"),
            common_button_startstop: ("commons/StartStopButton"),
            common_button_delete: ("commons/DeleteButton"),
            common_button_reset: ("commons/ResetButton"),
            common_statelabel: ("commons/StateLabel"),
            common_rate: ("commons/Rate"),
            common_yesno: ("commons/YesNo"),
            common_tabs: ("commons/Tabs"),

            common_login: ("commons/Login"),

            core_mixin: "/assets/javascripts/core/CoreMixin",
            paginationMixin: "/assets/javascripts/core/PaginationMixin"

        },
        packages: [
        ],
        shim: {
            bootstrap: {
                deps: ["jquery"]
            },
            crypto_sha256: {
                deps: ["crypto_core"]
            },
            packery: {
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

    require(['common_link_edit']);
    require(['packery']);

})();
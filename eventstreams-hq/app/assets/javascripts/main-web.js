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

            common_nodetabs: adminApp("content/commons/ClusterNodesTabs"),
            common_editor_mixin: adminApp("content/commons/JSONEditorModalMixin"),
            common_button_startstop: adminApp("content/commons/StartStopButton"),
            common_button_delete: adminApp("content/commons/DeleteButton"),
            common_button_reset: adminApp("content/commons/ResetButton"),
            common_statelabel: adminApp("content/commons/StateLabel"),
            common_rate: adminApp("content/commons/Rate"),
            common_yesno: adminApp("content/commons/YesNo"),
            common_tabs: adminApp("content/commons/Tabs"),



            lz: "/assets/javascripts/lz-string",
            core_mixin: "/assets/javascripts/tools/CoreMixin",
            paginationMixin: "/assets/javascripts/tools/PaginationMixin",
            logging: "/assets/javascripts/tools/Logging",
            wsclient: "/assets/javascripts/tools/ServerClient"
        },
        packages: [
            'admin/app'
        ],
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

    require(["admin/main"]);

})();
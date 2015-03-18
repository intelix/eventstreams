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

    require(["/assets/javascripts/common-config.js"]);

    require.config({

        /*noinspection */
        packages: [
            'hq/app'
        ]
    });

    require([
        "/assets/javascripts/agents/config.js",
        "/assets/javascripts/gates/config.js",
        "/assets/javascripts/flows/config.js",
        "/assets/javascripts/auth/config.js",
        "hq/main"
    ]);

    /** Add plugins here **/
    require(["/assets/javascripts/desktopnotifications/config.js"]);
    require(["/assets/javascripts/gauges/config.js"]);

})();
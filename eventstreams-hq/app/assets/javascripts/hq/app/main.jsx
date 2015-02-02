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

define(['toastr', 'react', 'core_mixin', 'common_login',
        './SecuredContent'],
    function (toastr, React, core_mixin,
              Login,
              SecureContent) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function () {
                return "app/layout";
            },

            subscriptionConfig: function (props) {
                return [
                    {
                        address: 'local',
                        route: "_",
                        topic: 'cmd',
                        onData: this.onCmdData
                    },
                    {
                        address: 'local',
                        route: ":auth",
                        topic: 'permissions',
                        onData: this.onAuthData
                    }
                ];
            },

            getInitialState: function () {
                return {token: false}
            },

            onDisconnected: function () {
                this.popupWarn("Disconnected from the server  ... ");
            },

            onConnected: function () {
                this.popupInfo("Connection established");
            },

            popupError: function (msg) {
                toastr.options = {
                    "closeButton": false,
                    "debug": false,
                    "progressBar": false,
                    "positionClass": "toast-top-right",
                    "onclick": null,
                    "showDuration": "300",
                    "hideDuration": "1000",
                    "timeOut": "3000",
                    "extendedTimeOut": "1000",
                    "showEasing": "swing",
                    "hideEasing": "linear",
                    "showMethod": "fadeIn",
                    "hideMethod": "fadeOut"
                };
                toastr.error(msg, "Error");
            },

            popupWarn: function (msg) {
                toastr.options = {
                    "closeButton": false,
                    "debug": false,
                    "progressBar": false,
                    "positionClass": "toast-top-right",
                    "onclick": null,
                    "showDuration": "300",
                    "hideDuration": "1000",
                    "timeOut": "3000",
                    "extendedTimeOut": "1000",
                    "showEasing": "swing",
                    "hideEasing": "linear",
                    "showMethod": "fadeIn",
                    "hideMethod": "fadeOut"
                };
                toastr.warning(msg);
            },

            popupInfo: function (msg) {
                toastr.options = {
                    "closeButton": false,
                    "debug": false,
                    "progressBar": false,
                    "positionClass": "toast-top-right",
                    "onclick": null,
                    "showDuration": "300",
                    "hideDuration": "1000",
                    "timeOut": "3000",
                    "extendedTimeOut": "1000",
                    "showEasing": "swing",
                    "hideEasing": "linear",
                    "showMethod": "fadeIn",
                    "hideMethod": "fadeOut"
                };
                toastr.info(msg);
            },

            onCmdData: function (data) {
                if (data) {
                    if (data.error) {
                        this.popupError(data.error.msg);
                    }
                    if (data.ok) {
                        this.popupInfo(data.ok.msg);
                    }
                }
            },

            onAuthData: function (data) {
                alert("!>>>>> auth data: " + JSON.stringify(data));
            },

            componentWillMount: function () {
                this.setState({token: this.readCookie("_eventstreams_token")});
            },

            render: function () {

                var token = this.state.token;

                if (token) {
                    return <SecureContent {...this.props} />;

                } else {
                    return <Login {...this.props} />;
                }
            }
        });

    });
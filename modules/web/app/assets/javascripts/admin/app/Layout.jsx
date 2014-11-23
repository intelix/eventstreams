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

define(['toastr', 'react', 'coreMixin', 'subscriberMixin',
        'app_navbar',
        'app_content'],
    function (toastr, React, coreMixin, subscriberMixin,
              Navbar,
              Content) {

        return React.createClass({
            mixins: [coreMixin, subscriberMixin],

            subscriptionConfig: function (props, state) {
                return {
                    address: 'local',
                    route: "_",
                    topic: 'cmd',
                    target: 'cmdresult'
                };
            },


            getInitialState: function () {
                return {result: false}
            },

            onDisconnected: function() {
                this.popupWarn("Disconnected from the server  ... ");
            },

            onConnected: function() {
                this.popupInfo("Connection established");
            },

            popupError: function(msg) {
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

            popupWarn: function(msg) {
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

            popupInfo: function(msg) {
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

            onComponentUpdate: function (nextProps, nextState) {
                if (nextState.cmdresult) {
                    if (nextState.cmdresult.error) {
                        this.popupError(nextState.cmdresult.error.msg);
                    }
                    if (nextState.cmdresult.ok) {
                        this.popupinfo(nextState.cmdresult.ok.msg);
                    }
                    this.setState({result: false});
                }
            },

            render: function () {
                return (
                    <span>
                        <Navbar {...this.props} />
                        <Content {...this.props} />
                    </span>
                )
            }
        });

    });
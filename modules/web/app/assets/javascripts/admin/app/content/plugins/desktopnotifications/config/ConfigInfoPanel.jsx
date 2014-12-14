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

define(['react', 'core_mixin', 'common_button_edit', './SubscribeButton'],
    function (React, core_mixin, EditButton, SubscribeButton) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function () {
                return "plugin/desktopnotifications/config/ConfigInfoPanel/" + this.props.ckey;
            },

            getInitialState: function () {
                return {info: false, stats: false}
            },

            subscriptionConfig: function (props) {
                return [
                    {address: props.addr, route: props.ckey, topic: 'info', dataKey: 'info'}
                ];
            },

            render: function () {
                var self = this;

                var info = self.state.info;


                var body;
                if (!info) {
                    body = <div className="panel-body">Loading...</div>;
                } else {
                    body = (
                        <div className="panel-body">

                            <div className="withspace">
                                <EditButton {...self.props} eventId="editSignalSubscription" />
                                <SubscribeButton {...self.props}/>
                            </div>
                        </div>

                    );
                }

                return (
                    <div className="panel panel-default">
                        <div className="panel-heading">Configuration</div>
                        {body}
                    </div>
                );

            }

        });

    });
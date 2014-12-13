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

define(['react', 'core_mixin', './ReplayButton'],
    function (React, core_mixin, ReplayButton) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/content/gates/config/ReplayPanel/" + this.props.ckey; },

            getInitialState: function () {
                return {info: false}
            },


            subscriptionConfig: function (props) {
                return [
                    {address: props.addr, route: props.ckey, topic: 'info', dataKey: 'info'}
                ];
            },

            render: function () {
                var self = this;
                var info = self.state.info;

                var replayButton;
                if (info.replaySupported) {
                    replayButton = <ReplayButton {...self.props} enabled={info.state == 'active' || info.state == 'error'} />;
                } else {
                    replayButton = "Events retention is required for replay functionality";
                }

                var body;
                if (!info) {
                    body = <div className="panel-body">Loading...</div>;
                } else {
                    body = (
                        <div className="panel-body">
                            <div>
                            {replayButton}
                            </div>
                        </div>
                    );
                }

                return (
                    <div className="panel panel-default">
                        <div className="panel-heading">Events replay</div>
                        {body}
                    </div>
                );

            }

        });

    });
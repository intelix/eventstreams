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

define(['react', 'core_mixin'], function (React, core_mixin) {

    // use this.sendCommand(subject, data) to talk to server

    return React.createClass({

        mixins: [core_mixin],

        componentName: function() { return "app/content/commons/EditLink/" + this.props.ckey; },

        getInitialState: function () {
            return {connected: false}
        },


        handleEditLinkClick: function () {
                this.raiseEvent(this.props.editEvent, {ckey: this.props.ckey});
            },

            render: function () {

                var self = this;

                var mainLink = self.props.text;
                if (self.state.connected && self.hasTopicPermission(self.props.ckey, "update_props")) {
                    mainLink = <a href="#" onClick={self.handleEditLinkClick} >{mainLink}</a>;
                }

                return <span>{mainLink}</span>;
            }
        });

    });
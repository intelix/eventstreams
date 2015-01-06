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

    return React.createClass({
        mixins: [core_mixin],

        componentName: function() { return "app/content/ds/AgentName"; },


        subscriptionConfig: function (props) {
            return [{address:props.addr, route:props.ckey, topic:'info', dataKey: 'info'}];
        },

        getInitialState: function () {
            return {info: false}
        },

        renderData: function() {
            return (
                <a href="#">{this.state.info.name} @ {this.state.info.location}</a>
            );
        },

        renderLoading: function() {
            return (
                <a href="#">loading...</a>
            );
        },

        render: function () {
            if (this.state.info) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});
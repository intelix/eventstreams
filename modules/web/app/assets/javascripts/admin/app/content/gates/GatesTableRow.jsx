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

define(['react', 'coreMixin', 'streamMixin'], function (React, coreMixin, streamMixin) {

    return React.createClass({
        mixins: [coreMixin, streamMixin],

        subscriptionConfig: function (props) {
            return [{address: props.addr, route: props.id, topic: 'info', dataKey: 'info'}];
        },
        getInitialState: function () {
            return {info: false}
        },

        handleClick: function() {
            this.raiseEvent("editGate", {id: this.props.id});
        },

        renderData: function () {
            return <tr>
                <td><a href="#" onClick={this.handleClick}>{this.state.info.name}</a></td>
                <td>Retention</td>
                <td>Retained data</td>
                <td>In-flight</td>
                <td>Current rate</td>
                <td>Avg events/day</td>
                <td>Active datasources</td>
                <td>Active sinks</td>
                <td>Inactive sinks</td>
                <td>Created</td>
                <td>Last state change</td>
                <td>{this.state.info.state}</td>
                <td></td>
            </tr>;
        },
        renderLoading: function () {
            return (
                <tr><td colSpan="12">loading...</td></tr>
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
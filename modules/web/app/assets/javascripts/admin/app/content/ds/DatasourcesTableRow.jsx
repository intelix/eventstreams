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

define(['react', 'coreMixin', 'streamMixin', 'app_content_button_startstop', 'app_content_button_delete', 'app_content_button_reset'],
    function (React, coreMixin, streamMixin, StartStopButton, DeleteButton, ResetButton) {

    return React.createClass({
        mixins: [coreMixin, streamMixin],

        componentName: function() { return "app/content/ds/TableRow/" + this.props.id; },

        subscriptionConfig: function (props) {
            return [{address: props.addr, route: props.id, topic: 'info', dataKey: 'info'}];
        },
        getInitialState: function () {
            return {info: false}
        },

        handleClick: function() {
            this.raiseEvent("editDatasource", {id: this.props.id});
        },

        renderData: function () {

            var state;

            var info = this.state.info;

            switch (info.state) {
                case "active": state = <span className="label label-success">open - ok</span>; break;
                case "passive": state = <span className="label label-default">closed</span>; break;
                default: state = <span className="label label-warning">unknown - this.state.info.state</span>; break;
            }

            return <tr>

                <td><a href="#" onClick={this.handleClick}>{info.name}</a></td>
                <td>[{info.endpointType}] {info.endpointDetails}</td>
                    <td>10/min</td>
                <td>{info.created}</td>
                <td>{info.sinceStateChange}</td>
                <td>{state}</td>
                <td>
                    <StartStopButton {...this.props} state={info.state} route={this.props.id} />
                    <DeleteButton {...this.props} route={this.props.id} />
                    <ResetButton {...this.props} route={this.props.id} />
                </td>
            </tr>;
        },
        renderLoading: function () {
            return (
                <tr><td colSpan="6">loading...</td></tr>
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
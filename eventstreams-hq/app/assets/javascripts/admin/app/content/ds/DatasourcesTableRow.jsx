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

define(['react', 'core_mixin', 'common_button_startstop', 'common_button_delete', 'common_button_reset'],
    function (React, core_mixin, StartStopButton, DeleteButton, ResetButton) {

    return React.createClass({
        mixins: [core_mixin],

        componentName: function() { return "app/content/ds/TableRow/" + this.props.ckey; },

        subscriptionConfig: function (props) {
            return [{address: props.addr, route: props.ckey, topic: 'info', dataKey: 'info'}];
        },
        getInitialState: function () {
            return {info: false}
        },

        handleClick: function() {
            this.raiseEvent("editDatasource", {ckey: this.props.ckey});
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
                <td>{info.created}</td>
                <td>{info.sinceStateChange}</td>
                <td>{state}</td>
                <td>
                    <StartStopButton {...this.props} state={info.state} />
                    <DeleteButton {...this.props}  />
                    <ResetButton {...this.props} />
                </td>
            </tr>;
        },
        renderLoading: function () {
            return (
                <tr><td colSpan="5">loading...</td></tr>
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
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

define(['react', 'coreMixin', 'streamMixin', 'app_content_button_startstop', 'app_content_button_delete'],
    function (React, coreMixin, streamMixin, StartStopButton, DeleteButton) {

    return React.createClass({
        mixins: [coreMixin, streamMixin],

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
            switch (this.state.info.state) {
                case "active": state = <span className="label label-success">open - ok</span>; break;
                case "passive": state = <span className="label label-default">closed</span>; break;
                default: state = <span className="label label-warning">unknown - this.state.info.state</span>; break;
            }

            return <tr>

                <td><a href="#" onClick={this.handleClick}>{this.state.info.name}</a></td>
                <td>akka.tcp://ehub@localhost:12345/user/gatename</td>
                    <td>10/min</td>
                    <td>1 month ago</td>
                <td>{state}</td>
                <td>
                    <StartStopButton {...this.props} state={this.state.info.state} route={this.props.id} />
                    <DeleteButton {...this.props} route={this.props.id} />
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
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

define(['react', 'coreMixin', 'streamMixin', 'visibilityMixin', 'app_content_button_startstop', 'app_content_button_delete', 'app_gates_button_replay'],
    function (React, coreMixin, streamMixin, visibilityMixin, StartStopButton, DeleteButton, ReplayButton) {

    return React.createClass({
        mixins: [coreMixin, streamMixin, visibilityMixin],

        componentName: function() { return "app/content/gates/TableRow/" + this.props.id; },

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
            var self = this;

            var info = self.state.info;

            var state;
            switch (info.state) {
                case "active": state = <span className="label label-success">open{info.stateDetails ? " - " + info.stateDetails : ""}</span>; break;
                case "passive": state = <span className="label label-default">closed{info.stateDetails ? " - " + info.stateDetails : ""}</span>; break;
                case "replay": state = <span className="label label-info">replaying{info.stateDetails ? " - " + info.stateDetails : ""}</span>; break;
                case "error": state = <span className="label label-danger">error{info.stateDetails ? " - " + info.stateDetails : ""}</span>; break;
                case "unknown": state = <span className="label label-warning">n/a{info.stateDetails ? " - " + info.stateDetails : ""}</span>; break;
                default: state = <span className="label label-warning">unknown - {info.state}</span>; break;
            }

            var mainLink = info.name;
            if (self.state.connected) {
                mainLink = <a href="#" onClick={this.handleClick} >{mainLink}</a>;
            }

            var replayButton;
            if (info.replaySupported) {
                replayButton = <ReplayButton {...self.props} enabled={info.state == 'passive'} route={self.props.id} />;
            } else {
                replayButton = "";
            }

            return <tr ref='monitorVisibility'>
                <td>{mainLink}</td>
                <td>{info.address}</td>
                <td>{info.retention}</td>
                <td>100,237</td>
                <td>{info.overflow}</td>
                <td>10</td>
                <td>10/min</td>
                <td>1,007</td>
                <td><span className="label label-success">5</span></td>
                <td><span className="label label-success">3</span><span className="label label-default">1</span></td>
                <td>{info.created}</td>
                <td>{info.sinceStateChange}</td>
                <td>{state}</td>
                <td>
                    <StartStopButton {...self.props} state={info.state} route={self.props.id} />
                    <DeleteButton {...self.props} route={self.props.id} />
                    {replayButton}
                </td>
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
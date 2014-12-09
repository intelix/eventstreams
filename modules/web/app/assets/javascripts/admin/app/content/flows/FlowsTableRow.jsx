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

        componentName: function() { return "app/content/flows/TableRow/" + this.props.id; },


        subscriptionConfig: function (props) {
            return [
                {address: props.addr, route: props.id, topic: 'info', dataKey: 'info'},
                {address: props.addr, route: props.id, topic: 'dyninfo', dataKey: 'dyninfo'}
            ];
        },
        getInitialState: function () {
            return {info: false, dyninfo: false}
        },

        handleClick: function() {
            this.raiseEvent("editFlow", {id: this.props.id});
        },

        renderData: function () {
            var self = this;

            var info = self.state.info;
            var dyninfo = self.state.dyninfo;

            var state;
            switch (info.state) {
                case "active": state = <span className="label label-success">{info.stateDetails}</span>; break;
                case "passive": state = <span className="label label-default">{info.stateDetails}</span>; break;
                case "error": state = <span className="label label-danger">{info.stateDetails}</span>; break;
                case "unknown": state = <span className="label label-warning">{info.stateDetails}</span>; break;
                default: state = <span className="label label-warning">unknown - {info.state}</span>; break;
            }

            var mainLink = info.name;
            if (self.state.connected) {
                mainLink = <a href="#" onClick={this.handleClick} >{mainLink}</a>;
            }

            var inRate = "N/A";
            if (dyninfo.inrate) inRate = dyninfo.inrate +"/s";
            var outRate = "N/A";
            if (dyninfo.outrate) outRate = dyninfo.outrate +"/s";


            return <tr>
                <td>{mainLink}</td>
                <td>{inRate}</td>
                <td>{outRate}</td>
                <td>{info.created}</td>
                <td>{info.sinceStateChange}</td>
                <td>{state}</td>
                <td>
                    <StartStopButton {...this.props} state={info.state} route={this.props.id} />
                    <DeleteButton {...this.props} route={this.props.id} />
                </td>
            </tr>;
        },
        renderLoading: function () {
            return (
                <tr><td colSpan="7">loading...</td></tr>
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
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
define([
        'react',
        'coreMixin',
        'streamMixin',
        'admin/gate/StartStopButton',
        'admin/gate/DeleteButton'
    ],
    function (React,
              coreMixin,
              streamMixin,
              StartStopButton,
              DeleteButton) {

        return React.createClass({
            mixins: [coreMixin, streamMixin],

            subscriptionConfig: function (props) {
                return [{address: props.addr, route: props.id, topic: 'info', dataKey: 'info'}];
            },
            getInitialState: function () {
                return {info: null}
            },

            renderData: function () {
                return (
                    <div>
                    {this.state.info.name} {this.state.info_stale ? "Stale" : "not stale"} : {this.state.info.text} :
                        <StartStopButton {...this.props} state={this.state.info.state} route={this.props.id} />
                    :
                        <DeleteButton  {...this.props} route={this.props.id} />
                    </div>
                );
            },
            renderLoading: function () {
                return (
                    <div>loading...</div>
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
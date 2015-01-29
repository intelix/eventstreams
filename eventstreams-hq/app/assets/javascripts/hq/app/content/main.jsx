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

define(
    ['react', 'core_mixin', './GroupContents'],
    function (React, core_mixin, GroupContents) {

        return React.createClass({
            mixins: [core_mixin],

            componentName: function() { return "app/content/ContentManager"; },


            getInitialState: function () {
                return { list: false, selection: false }
            },

            subscriptionConfig: function (props) {
                return [{
                    address: 'local',
                    route: "hqgroups",
                    topic: 'list',
                    dataKey: 'list'
                }];
            },


            componentDidMount: function() {
                this.addEventListener("navBarSelection", this.handleSelectionEvent);
            },

            componentWillUnmount: function() {
                this.removeEventListener("navBarSelection", this.handleSelectionEvent);
            },

            handleSelectionEvent: function(evt) {
                this.setState({selection: evt.detail.groupKey});
            },

            render: function () {

                var self = this;
                var content = "";

                var services = (!self.state.list || !self.state.list.services ? [] : self.state.list.services)
                    .filter(function(next) {return next.group == self.state.selection});

                if (services) {
                    content = <GroupContents services={services} {...this.props} />
                }

                return <div className="container-fluid main-container" role="main"><span>{content}</span></div>
            }
        });

    });
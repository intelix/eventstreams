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

define(['react', 'core_mixin', './SidebarElement'], function (React, core_mixin, SidebarElement) {

    return React.createClass({
        mixins: [core_mixin],

        getInitialState: function () {
            return {selection: false}
        },


        componentName: function () {
            return "app/Contents";
        },

        componentDidMount: function () {
            this.addEventListener("sideBarSelection", this.handleSelectionEvent);
        },

        componentWillUnmount: function () {
            this.removeEventListener("sideBarSelection", this.handleSelectionEvent);
        },

        handleSelectionEvent: function (evt) {
            this.setState({selection: evt.detail.menuKey});
        },

        render: function () {
            var self = this;

            var currentSelection = self.state.selection;
            if (self.props.services[0] && 
                (!currentSelection  || !self.props.services.some(function (next) {
                    return next.id == currentSelection
                }))) {
                currentSelection = self.props.services[0].id;
            }

            var content = "";

            if (currentSelection) {
                var Node = require(currentSelection);
                content = <Node {...this.props} />
            }

            return (
                <div className="row">
                    <div className="col-md-2">
                        <ul className="nav nav-pills dark nav-stacked">
                        {self.props.services.map(function (next) {
                            return <SidebarElement menuKey={next.id} name={next.name} key={next.id} active={currentSelection == next.id}/>
                        })}
                        </ul>
                    </div>
                    <div className="col-md-10">
                    {content}
                    </div>

                </div>
            );
        }
    });

});
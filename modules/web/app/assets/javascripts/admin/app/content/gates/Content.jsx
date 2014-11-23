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

define(['react', 'coreMixin', 'admin/app/content/commons/ClusterNodesTabs', 'admin/gate/ListContainer'], function (React, coreMixin, Tabs, ListContainer) {

    return React.createClass({
        mixins: [coreMixin],

        getInitialState: function () {
            return { selected: false }
        },

        onMount: function() {
            this.addEventListener("nodeSelectorForGates", this.handleSelectionEvent);
        },
        onUnmount: function() {
            this.removeEventListener("nodeSelectorForGates", this.handleSelectionEvent);
        },

        handleSelectionEvent: function(evt) {
            this.setState({selected: evt.detail.address});
        },


        render: function () {

            var content =  this.state.selected ? <ListContainer addr={this.state.selected} /> : <div>none selected</div>;


                return <div>
                <div className="jumbotron">
                    <h1>Gates</h1>
                    <p>This is a template for a simple marketing or informational website. It includes a large callout called a jumbotron and three supporting pieces of content. Use it as a starting point to create something more unique.</p>
                    <p>
                        <a href="#" className="btn btn-primary btn-lg" role="button">Learn more &raquo;</a>
                    </p>
                </div>
                <Tabs roles={["hq"]} selectorId="nodeSelectorForGates" />
                {content}
            </div>;
        }
    });

});
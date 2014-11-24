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
define(['react', 'coreMixin', 'subscriberMixin', 'admin/AdminContainer'], function (React, coreMixin, subscriberMixin, Container) {

    return React.createClass({
        mixins: [coreMixin, subscriberMixin],

        subscriptionConfig: function (props) {
            return {address:'local', route:'cluster', topic:'nodes', target: 'nodes'};
        },
        getInitialState: function () {
            return {nodes: null}
        },

        renderData: function() {

            var cx = React.addons.classSet;
            var connected = this.state.connected;

            return (
                <div>
                    <ul className="nav nav-tabs" role="tablist">
                        {this.state.nodes.map(function (el) {

                            var tabClasses = cx({
                                'disabled': (!connected || el.state != 'up'),
                                'active': $.inArray('adminweb', el.roles) > -1
                            });

                            return  <li key={el.id} role="presentation" className={tabClasses}><a href="#">{el.address} ({el.state})</a></li>;
                            })}
                    </ul>
                    <Container addr='akka.tcp://application@localhost:2551' />
                </div>
            );
        },
        renderLoading: function() {
            return (
                <div>loading...</div>
            );
        },

        render: function () {
            if (this.state.nodes) {
                return this.renderData();
            } else {
                return this.renderLoading();
            }
        }
    });

});
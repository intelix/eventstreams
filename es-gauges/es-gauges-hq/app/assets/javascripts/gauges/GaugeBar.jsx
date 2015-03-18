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


define(['react', 'core_mixin'], function (React, core_mixin) {


    return React.createClass({
        mixins: [core_mixin],

        componentName: function () {
            return "app/content/gauges/Bar/" + this.props.id;
        },

        getInitialState: function () {
            return {}
        },


        render: function () {
            var self = this;
            var props = self.props;

            var s = {
                width: "60%"
            };

            var barClasses = this.cx({
                'instrument-bar-blue': (self.props.displayValue != "c"),
                'instrument-bar-green': (self.props.displayValue == "c" && !self.props.isRed && !self.props.isYellow),
                'instrument-bar-yellow': (self.props.displayValue == "c" && self.props.isYellow),
                'instrument-bar-red': (self.props.displayValue == "c" && self.props.isRed),
                'instrument-bar': true
            });

            return (
                <div className="instrument">
                    <div className={barClasses} style={s}>
                        <div className="instrument-bar-text">90.01</div>
                    </div>
                </div>
            );
        }

    });


});
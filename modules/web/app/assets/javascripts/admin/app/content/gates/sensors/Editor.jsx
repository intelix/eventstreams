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

define(['react', 'core_mixin', 'common_editor_mixin'], function (React, core_mixin, editorMixin) {

    return React.createClass({
        mixins: [core_mixin, editorMixin],

        componentName: function () {
            return "app/content/gates/sensors/Editor";
        },

        schema: function () {
            return {
                "$schema": "http://json-schema.org/draft-04/schema#",
                "type": "object",
                "title": "Sensor configuration",
                "properties": {
                    "name": {
                        "propertyOrder": 10,
                        "title": "Name",
                        "type": "string"
                    },
                    "initialState": {
                        "propertyOrder": 30,
                        "title": "Initial state",
                        "type": "string",
                        "enum": [
                            "Active",
                            "Passive"
                        ]
                    },
                    "simpleCondition": {
                        "propertyOrder": 31,
                        "$ref": "#/definitions/simpleCondition"
                    },
                    "occurrenceCondition": {
                        "propertyOrder": 32,
                        "title": "Trigger if ...",
                        "type": "string",
                        "enum": [
                            "Exactly",
                            "Less than",
                            "More than"
                        ]
                    },
                    "occurrenceCount": {
                        "propertyOrder": 33,
                        "title": "Number of events",
                        "type": "number"
                    },
                    "occurrenceWatchPeriodSec": {
                        "propertyOrder": 33,
                        "title": "Occurred in the following time window (sec)",
                        "type": "number"
                    },
                    "signalClass": {
                        "propertyOrder": 35,
                        "title": "Signal class",
                        "type": "string"
                    },
                    "signalSubclass": {
                        "propertyOrder": 36,
                        "title": "Signal subclass",
                        "type": "string"
                    },
                    "level": {
                        "propertyOrder": 38,
                        "title": "Signal level",
                        "type": "string",
                        "enum": [
                            "Very low",
                            "Low",
                            "Medium",
                            "High",
                            "Very high",
                            "Maximum"
                        ]
                    },
                    "title": {
                        "propertyOrder": 40,
                        "title": "Title template",
                        "type": "string"
                    },
                    "body": {
                        "propertyOrder": 42,
                        "title": "Body template",
                        "format": "textarea",
                        "type": "string"
                    },
                    "icon": {
                        "propertyOrder": 44,
                        "title": "Icon (URL) template",
                        "type": "string"
                    },
                    "correlationIdTemplate": {
                        "propertyOrder": 46,
                        "title": "Correlation ID template",
                        "type": "string"
                    },
                    "transactionMarking": {
                        "propertyOrder": 48,
                        "title": "Transaction demarcation",
                        "type": "string",
                        "enum": [
                            "None",
                            "Start",
                            "Middle",
                            "Success",
                            "Failure"
                        ]
                    },
                    "timestampSource": {
                        "propertyOrder": 60,
                        "title": "Timestamp source (field ref)",
                        "type": "string"
                    }
                },
                "additionalProperties": false,
                "required": [
                    "name",
                    "initialState",
                    "simpleCondition",
                    "occurrenceCondition",
                    "occurrenceCount",
                    "occurrenceWatchPeriodSec",
                    "signalClass",
                    "title"
                ],
                "definitions": {
                    "simpleCondition": {
                        "type": "string",
                        "title": "Condition"
                    }
                }
            };

        }

    });

});
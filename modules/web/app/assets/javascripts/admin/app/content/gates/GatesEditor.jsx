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

define(['react', 'coreMixin', 'streamMixin', 'app_content_editor_mixin'], function (React, coreMixin, streamMixin, editorMixin) {

    return React.createClass({
        mixins: [coreMixin, streamMixin, editorMixin],

        schema: function () {
            return {
                "$schema": "http://json-schema.org/draft-04/schema#",
                "type": "object",
                "title": "Gate configuration",
                "properties": {
                "name": {
                    "propertyOrder": 10,
                        "title": "Name",
                        "type" : "string"
                },
                "desc": {
                    "propertyOrder": 20,
                        "title": "Description",
                        "type" : "string"
                },
                "initialState": {
                    "propertyOrder": 30,
                        "title": "Initial state",
                        "type": "string",
                        "enum": ["Open", "Closed"]

                },
                "maxInFlight": {
                    "propertyOrder": 40,
                        "title": "Max in-flight messages",
                        "type" : "integer",
                        "minimum": 1
                },
                "overflowPolicy": {
                    "propertyOrder": 50,
                        "title": "Overflow policy",
                        "type": "object",
                        "oneOf": [
                        { "$ref": "#/definitions/overflowBackpressure", "title":"Backpressure" },
                        { "$ref": "#/definitions/overflowDrop", "title":"Drop events" }
                    ]
                },
                "retentionPolicy": {
                    "propertyOrder": 60,
                        "title": "Retention policy",
                        "type": "object",
                        "oneOf": [
                        { "$ref": "#/definitions/retentionNone", "title":"None (all events dropped after processing)" },
                        { "$ref": "#/definitions/retentionDays", "title":"Days" },
                        { "$ref": "#/definitions/retentionCount", "title":"Events count" }
                    ]
                }

            },
                "additionalProperties": true,
                "required": [ "name", "initialState","maxInFlight","overflowPolicy" ],
                "definitions": {
                "retentionNone": {
                    "type": "object",
                        "properties": {
                        "type": {
                            "propertyOrder": 10,
                                "title": "Type",
                                "type" : "string",
                                "template": "none"
                        }
                    },
                    "additionalProperties": false
                },
                "retentionDays": {
                    "type": "object",
                        "properties": {
                        "type": {
                            "propertyOrder": 10,
                                "title": "Type",
                                "type" : "string",
                                "template": "days"
                        },
                        "count": {
                            "propertyOrder": 20,
                                "title": "Days to keep",
                                "type" : "integer",
                                "minimum": 1
                        }
                    },
                    "additionalProperties": false
                },
                "retentionCount": {
                    "type": "object",
                        "properties": {
                        "type": {
                            "propertyOrder": 10,
                                "title": "Type",
                                "type" : "string",
                                "template": "count"
                        },
                        "count": {
                            "propertyOrder": 20,
                                "title": "Number of events to keep",
                                "type" : "integer",
                                "minimum": 1
                        }
                    },
                    "additionalProperties": false
                },
                "overflowBackpressure": {
                    "type": "object",
                        "properties": {
                        "type": {
                            "propertyOrder": 10,
                                "title": "Type",
                                "type" : "string",
                                "template": "backpressure"
                        }
                    },
                    "additionalProperties": false
                },
                "overflowDrop": {
                    "type": "object",
                        "properties": {
                        "type": {
                            "propertyOrder": 10,
                                "title": "Type",
                                "type" : "string",
                                "template": "drop"
                        },
                        "policy": {
                            "propertyOrder": 20,
                                "title": "Policy",
                                "type" : "string",
                                "enum": ["Latest","Newest"]
                        }
                    },
                    "additionalProperties": false
                }
            }
            };

        }

    });

});
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

        componentName: function() { return "app/content/flows/Editor"; },

        schema: function () {
            return {
                "$schema": "http://json-schema.org/draft-04/schema#",
                "title": "Flow configuration",
                "type": "object",
                "properties": {
                    "name": {
                        "propertyOrder": 10,
                        "title": "Name",
                        "type": "string"
                    },
                    "desc": {
                        "propertyOrder": 20,
                        "title": "Description",
                        "type": "string"
                    },
                    "initialState": {
                        "propertyOrder": 30,
                        "title": "Initial state",
                        "type": "string",
                        "enum": [
                            "Started",
                            "Stopped"
                        ]
                    },
                    "tap": {
                        "propertyOrder": 40,
                        "title": "Tap configuration",
                        "type": "object",
                        "oneOf": [
                            {
                                "$ref": "#/definitions/tapGate",
                                "title": "Gate"
                            },
                            {
                                "$ref": "#/definitions/tapStatsd",
                                "title": "Statsd"
                            },
                            {
                                "$ref": "#/definitions/tapUDP",
                                "title": "UDP"
                            }
                        ]
                    },
                    "pipeline": {
                        "propertyOrder": 50,
                        "title": "Pipeline configuration",
                        "type": "array",
                        "format": "tabs",
                        "items": {
                            "title": "Instruction",
                            "headerTemplate": "{{i}}: {{self.class}}",
                            "oneOf": [
                                {
                                    "$ref": "#/definitions/instructionLog",
                                    "title": "Log"
                                },
                                {
                                    "$ref": "#/definitions/instructionEnrich",
                                    "title": "Enrich"
                                },
                                {
                                    "$ref": "#/definitions/instructionAddTag",
                                    "title": "Add tag"
                                },
                                {
                                    "$ref": "#/definitions/instructionSplit",
                                    "title": "Split"
                                },
                                {
                                    "$ref": "#/definitions/instructionGrok",
                                    "title": "Grok"
                                },
                                {
                                    "$ref": "#/definitions/instructionGate",
                                    "title": "Gate"
                                },
                                {
                                    "$ref": "#/definitions/instructionGroovy",
                                    "title": "Groovy"
                                },
                                {
                                    "$ref": "#/definitions/instructionDrop",
                                    "title": "Drop"
                                },
                                {
                                    "$ref": "#/definitions/instructionDate",
                                    "title": "Date parser"
                                },
                                {
                                    "$ref": "#/definitions/instructionInflux",
                                    "title": "InfluxDB"
                                },
                                {
                                    "$ref": "#/definitions/instructionES",
                                    "title": "ElasticSearch"
                                }
                            ]
                        }
                    }
                },
                "additionalProperties": false,
                "required": [
                    "tap",
                    "pipeline"
                ],
                "definitions": {
                    "tapGate": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "gate"
                            },
                            "name": {
                                "propertyOrder": 10,
                                "title": "Gate name or full address",
                                "type": "string"
                            }
                        }
                    },
                    "tapStatsd": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "statsd"
                            },
                            "host": {
                                "propertyOrder": 10,
                                "title": "Hostname",
                                "type": "string"
                            },
                            "port": {
                                "propertyOrder": 20,
                                "title": "Port",
                                "type": "integer"
                            }
                        }
                    },
                    "tapUDP": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "udp"
                            },
                            "host": {
                                "propertyOrder": 10,
                                "title": "Hostname",
                                "type": "string"
                            },
                            "port": {
                                "propertyOrder": 20,
                                "title": "Port",
                                "type": "integer"
                            }
                        }
                    },
                    "simpleCondition": {
                        "type": "string",
                        "title": "Simple condition"
                    },
                    "condition": {
                        "title": "Complex condition",
                        "type": "object",
                        "oneOf": [
                            {
                                "$ref": "#/definitions/conditionNone",
                                "title": "None"
                            },
                            {
                                "$ref": "#/definitions/conditionAny",
                                "title": "Any ..."
                            },
                            {
                                "$ref": "#/definitions/conditionAll",
                                "title": "All ..."
                            },
                            {
                                "$ref": "#/definitions/conditionField",
                                "title": "Field match"
                            },
                            {
                                "$ref": "#/definitions/conditionTag",
                                "title": "Tag match"
                            }
                        ]
                    },
                    "conditionAll": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "all"
                            },
                            "list": {
                                "propertyOrder": 20,
                                "title": "All ...",
                                "type": "array",
                                "format": "table",
                                "items": {
                                    "title": "Criterion",
                                    "headerTemplate": "{{i}}: {{self.class}}",
                                    "oneOf": [
                                        {
                                            "$ref": "#/definitions/conditionAny",
                                            "title": "Any ..."
                                        },
                                        {
                                            "$ref": "#/definitions/conditionAll",
                                            "title": "All ..."
                                        },
                                        {
                                            "$ref": "#/definitions/conditionField",
                                            "title": "Field match"
                                        },
                                        {
                                            "$ref": "#/definitions/conditionTag",
                                            "title": "Tag match"
                                        }
                                    ]
                                }
                            }
                        },
                        "required": [
                            "class",
                            "list"
                        ]
                    },
                    "conditionAny": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "any"
                            },
                            "list": {
                                "propertyOrder": 20,
                                "title": "Any ...",
                                "type": "array",
                                "format": "table",
                                "items": {
                                    "title": "Criterion",
                                    "headerTemplate": "{{i}}: {{self.class}}",
                                    "oneOf": [
                                        {
                                            "$ref": "#/definitions/conditionAny",
                                            "title": "Any ..."
                                        },
                                        {
                                            "$ref": "#/definitions/conditionAll",
                                            "title": "All ..."
                                        },
                                        {
                                            "$ref": "#/definitions/conditionField",
                                            "title": "Field match"
                                        },
                                        {
                                            "$ref": "#/definitions/conditionTag",
                                            "title": "Tag match"
                                        }
                                    ]
                                }
                            }
                        },
                        "required": [
                            "class",
                            "list"
                        ]
                    },
                    "conditionField": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "field"
                            },
                            "name": {
                                "propertyOrder": 10,
                                "title": "Field name",
                                "type": "string"
                            },
                            "is": {
                                "propertyOrder": 20,
                                "title": "'is..' regex",
                                "type": "string"
                            },
                            "isnot": {
                                "propertyOrder": 20,
                                "title": "'is not..' regex",
                                "type": "string"
                            }
                        },
                        "required": [
                            "class",
                            "name"
                        ]
                    },
                    "conditionTag": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "tag"
                            },
                            "name": {
                                "propertyOrder": 10,
                                "title": "Tag name",
                                "type": "string"
                            },
                            "is": {
                                "propertyOrder": 20,
                                "title": "'is..' regex",
                                "type": "string"
                            },
                            "isnot": {
                                "propertyOrder": 20,
                                "title": "'is not..' regex",
                                "type": "string"
                            }
                        },
                        "required": [
                            "class",
                            "name"
                        ]
                    },
                    "conditionNone": {
                        "type": "object",
                        "properties": {}
                    },
                    "instructionDrop": {
                        "type": "object",
                        "title": "Drop event",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "drop"
                            },
                            "name": {
                                "propertyOrder": 10,
                                "title": "Name",
                                "type": "string"
                            },
                            "simpleCondition": {
                                "propertyOrder": 30,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": ["class"]
                    },
                    "instructionGate": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "gate"
                            },
                            "name": {
                                "propertyOrder": 20,
                                "title": "Name",
                                "type": "string"
                            },
                            "address": {
                                "propertyOrder": 30,
                                "title": "Gate name or address",
                                "type": "string"
                            },
                            "simpleCondition": {
                                "propertyOrder": 110,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": ["class","address"]
                    },
                    "instructionGrok": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "grok"
                            },
                            "name": {
                                "propertyOrder": 20,
                                "title": "Name",
                                "type": "string"
                            },
                            "source": {
                                "propertyOrder": 30,
                                "title": "Source field",
                                "type": "string"
                            },
                            "pattern": {
                                "propertyOrder": 40,
                                "title": "Pattern",
                                "type": "string"
                            },
                            "groups": {
                                "propertyOrder": 50,
                                "title": "List of groups",
                                "type": "string"
                            },
                            "values": {
                                "propertyOrder": 60,
                                "title": "List of values",
                                "type": "string"
                            },
                            "fields": {
                                "propertyOrder": 70,
                                "title": "List of target fields",
                                "type": "string"
                            },
                            "types": {
                                "propertyOrder": 80,
                                "title": "List of target types",
                                "type": "string"
                            },
                            "simpleCondition": {
                                "propertyOrder": 110,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": ["class"]
                    },
                    "instructionEnrich": {
                        "type": "object",
                        "title": "Enrich event",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "enrich"
                            },
                            "name": {
                                "propertyOrder": 20,
                                "title": "Name",
                                "type": "string"
                            },
                            "fieldName": {
                                "propertyOrder": 30,
                                "title": "Field name",
                                "type": "string"
                            },
                            "fieldValue": {
                                "propertyOrder": 40,
                                "title": "Value for enrichment",
                                "type": "string"
                            },
                            "targetType": {
                                "propertyOrder": 50,
                                "title": "Target type",
                                "type": "string",
                                "enum": [
                                    "String",
                                    "Boolean",
                                    "Number",
                                    "String array",
                                    "Number array",
                                    "Boolean array"
                                ]
                            },
                            "simpleCondition": {
                                "propertyOrder": 110,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": ["class"]
                    },
                    "instructionAddTag": {
                        "type": "object",
                        "title": "Add tag",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "addtag"
                            },
                            "name": {
                                "propertyOrder": 20,
                                "title": "Name",
                                "type": "string"
                            },
                            "tagName": {
                                "propertyOrder": 30,
                                "title": "Tag name",
                                "type": "string"
                            },
                            "simpleCondition": {
                                "propertyOrder": 110,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": ["class"]
                    },
                    "instructionSplit": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "split"
                            },
                            "name": {
                                "propertyOrder": 20,
                                "title": "Name",
                                "type": "string"
                            },
                            "source": {
                                "propertyOrder": 30,
                                "title": "Source field",
                                "type": "string"
                            },
                            "pattern": {
                                "propertyOrder": 40,
                                "title": "Split pattern",
                                "type": "string"
                            },
                            "simpleCondition": {
                                "propertyOrder": 110,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": ["class"]
                    },
                    "instructionGroovy": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "groovy"
                            },
                            "name": {
                                "propertyOrder": 10,
                                "title": "Name",
                                "type": "string"
                            },
                            "code": {
                                "propertyOrder": 20,
                                "title": "Code",
                                "type": "string",
                                "format": "textarea"
                            },
                            "simpleCondition": {
                                "propertyOrder": 30,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": [
                            "class",
                            "code"
                        ]
                    },
                    "instructionLog": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "log"
                            },
                            "name": {
                                "propertyOrder": 10,
                                "title": "Name",
                                "type": "string"
                            },
                            "level": {
                                "propertyOrder": 20,
                                "title": "Logging level",
                                "type": "string",
                                "enum": [
                                    "DEBUG",
                                    "INFO",
                                    "WARN",
                                    "ERROR"
                                ]
                            },
                            "logger": {
                                "propertyOrder": 20,
                                "title": "Logger",
                                "type": "string"
                            },
                            "simpleCondition": {
                                "propertyOrder": 30,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": [
                            "class",
                            "level",
                            "logger"
                        ]
                    },
                    "instructionDate": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "date"
                            },
                            "name": {
                                "propertyOrder": 20,
                                "title": "Name",
                                "type": "string"
                            },
                            "source": {
                                "propertyOrder": 30,
                                "title": "Source field",
                                "type": "string"
                            },
                            "pattern": {
                                "propertyOrder": 40,
                                "title": "Source pattern",
                                "type": "string"
                            },
                            "targetPattern": {
                                "propertyOrder": 50,
                                "title": "Target pattern",
                                "description": "default: yyyy-MM-dd'T'HH:mm:ss.SSSZZ",
                                "type": "string"
                            },
                            "sourceZone": {
                                "propertyOrder": 60,
                                "title": "Source timezone (default: local zone)",
                                "type": "string"
                            },
                            "targetZone": {
                                "propertyOrder": 70,
                                "title": "Target timezone (default: local zone)",
                                "type": "string"
                            },
                            "targetFmtField": {
                                "propertyOrder": 80,
                                "title": "Target formatted date field (default: date_fmt)",
                                "type": "string"
                            },
                            "targetTSField": {
                                "propertyOrder": 90,
                                "title": "Target timestamp field (default: date_ts)",
                                "type": "string"
                            },
                            "simpleCondition": {
                                "propertyOrder": 110,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": [
                            "class",
                            "source",
                            "pattern"
                        ]
                    },
                    "instructionInflux": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "influx"
                            },
                            "name": {
                                "propertyOrder": 20,
                                "title": "Name",
                                "type": "string"
                            },
                            "db": {
                                "propertyOrder": 30,
                                "title": "Database name",
                                "type": "string"
                            },
                            "series": {
                                "propertyOrder": 40,
                                "title": "Target series",
                                "type": "string"
                            },
                            "columns": {
                                "propertyOrder": 50,
                                "title": "Target columns",
                                "type": "string"
                            },
                            "points": {
                                "propertyOrder": 60,
                                "title": "Points source",
                                "type": "string"
                            },
                            "host": {
                                "propertyOrder": 70,
                                "title": "Host name",
                                "type": "string"
                            },
                            "port": {
                                "propertyOrder": 80,
                                "title": "Port",
                                "type": "integer"
                            },
                            "user": {
                                "propertyOrder": 90,
                                "title": "Username",
                                "type": "string"
                            },
                            "password": {
                                "propertyOrder": 100,
                                "title": "Password",
                                "type": "string"
                            },
                            "simpleCondition": {
                                "propertyOrder": 110,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        },
                        "required": [
                            "class",
                            "db",
                            "host",
                            "port",
                            "user"
                        ]
                    },
                    "instructionES": {
                        "type": "object",
                        "properties": {
                            "class": {
                                "propertyOrder": 10,
                                "title": "Type",
                                "type": "string",
                                "template": "es"
                            },
                            "name": {
                                "propertyOrder": 20,
                                "title": "Name",
                                "type": "string"
                            },
                            "branch": {
                                "propertyOrder": 30,
                                "title": "Source branch",
                                "type": "string"
                            },
                            "index": {
                                "propertyOrder": 40,
                                "title": "Target index",
                                "type": "string"
                            },
                            "cluster": {
                                "propertyOrder": 50,
                                "title": "Cluster name",
                                "type": "string"
                            },
                            "host": {
                                "propertyOrder": 70,
                                "title": "Host name",
                                "type": "string"
                            },
                            "port": {
                                "propertyOrder": 80,
                                "title": "Port",
                                "type": "integer"
                            },
                            "simpleCondition": {
                                "propertyOrder": 110,
                                "$ref": "#/definitions/simpleCondition"
                            },
                            "condition": {
                                "propertyOrder": 120,
                                "$ref": "#/definitions/condition"
                            }
                        }
                    }
                }
            }	;

        }

    });

});
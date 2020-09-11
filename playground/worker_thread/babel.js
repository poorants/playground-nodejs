"use strict";

var _worker_threads = require("worker_threads");

var _path = _interopRequireDefault(require("path"));

function _interopRequireDefault(obj) {
  return obj && obj.__esModule ? obj : { default: obj };
}

if (_worker_threads.isMainThread) {
  console.log("i'm Master!");
  var worker = new _worker_threads.Worker(_path["default"].join(__dirname, "babel.js"));
} else {
  console.log("i'm Worker!!");
}

console.log(_worker_threads.isMainThread);

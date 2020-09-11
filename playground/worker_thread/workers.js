// import { Worker, isMainThread } from "worker_threads";
// import path from "path";
const { Worker, isMainThread } = require("worker_threads");
const path = require("path");

if (isMainThread) {
  console.log(`i'm Master!`);
  const worker = new Worker(path.join(__dirname, "workers.js"));

  worker.start();
} else {
  console.log(`i'm Worker!!`);
}

function start() {
  console.log("test");
}
// const start = () => {
//   console.log("started!");
// };

const Parser = require("flora-sql-parser").Parser;
const parser = new Parser();
// const ast = parser.parse("create table pt_itsm_crypt_file_stat_ext( file_id			sb8, doc_id			sb8, rtn_code		sb4,  decrypt_date	ub4, delete_date		ub4, status			ub1, result_message	schr(1025) ) ");
// console.log(ast);

let data = [
  {
    name: "name1",
    one: 1,
    two: 1,
    three: 0,
  },
  {
    name: "name2",
    one: 1,
    two: 1,
    three: 0,
  },
  {
    name: "name3",
    one: 1,
    two: 0,
    three: 1,
  },
];

let a = data.reduce(
  (a, c, i) => {
    if (c.one) a.one = { ...a.one, [c.name]: {} };
    if (c.two) a.two = { ...a.two, [c.name]: {} };
    if (c.three) a.three = { ...a.three, [c.name]: {} };
    return { ...a };
  },
  { one: {}, two: {}, three: {} }
);

console.log(a);

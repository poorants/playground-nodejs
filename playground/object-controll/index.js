import axios from "axios";

const objectControll = new Object();

objectControll.main = () => {
  const data = axios.get("http://localhost:3000/api/v1/servers").then((res) => {
    res.data;
  });

  
};

export default objectControll;

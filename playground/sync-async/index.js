import fs from 'fs'
const syncAsync = new Object()

syncAsync.sync = async () => {
    const files = fs.readdirSync('.')
    for (let idx in files) console.log(files[idx])
    console.log('end sync')
}

syncAsync.async = () => {
  fs.readdir('.', (err, files) => {
    //   console.log(files)
      for (let idx in files) console.log(files[idx])
    })
    console.log('end async')
}

export default syncAsync
import fs from 'fs'
import util from 'util'

function stripJSComments(str) {
    return str.replace(/\/\*[\s\S]*?\*\/|([^:]|^)\/\/.*$/gm, '');
}

// (\/\*[\w\'\s\r\n\*]*\*\/)|(\/\/[\w\s\']*)|(\<![\-\-\s\w\>\/]*\>)


function whatisthis(str) {
    return str.replace(/\/\*.+?\*\/|\/\/.*(?=[\n\r])/g, '');
}

function mergeApi(apis, api){        
    /**
     * set tag
     */
    const urlList = api.url.split('/')

    if(urlList.length > 2) api.methods[0].tag = `/${urlList[1]}/${urlList[2]}`
    else api.methods[0].tag = `/${urlList[1]}`


    const rst = apis.filter(data => {
        if(data.url == api.url) {
            data.methods.push(api.methods[0])
            return true
        }
    })

    if(rst.length == 0) apis.push(api)
}

function getText(apis){
    let yaml = ''
    yaml += 'paths:\n'
    apis.forEach(api => {
        api.url
        yaml += `  ${api.url}:\n`
        api.methods.forEach(item => {
            yaml += `    ${item.method}:\n`

            yaml += `      tags:\n`
            yaml += `      - ${item.tag}\n`
            yaml += `      summary:\n`
        })
    })
    return yaml
}

const documentGetter = new Object()

documentGetter.main = (target, result) => {
    const orgData = fs.readFileSync(target).toString()
    // console.log(data)
    const cleanData = stripJSComments(orgData)
    const data = cleanData.split('\r\n').join(' ').split(',').join(' ').split(';').join(' ').split('\t').join(' ').split('"').join(' ').split('(').join(' ').split(')').join(' ').split('.').join(' ').split(' ')

    let isApis = false
    let isValue = false
    let isMethod = false
    let isProduces = false
    let wordCount = 0
    let valuePos = 0
    let methodPos = 0
    let producesPos = 0

    let resultString = ''
    
    let apis = new Array()
    let api = new Object()
    let method = {method:'', procedure:[]}

    data.forEach(word => {
        if(word == '') return
        // console.log(word)

        if(word == '@RequestMapping') {
            isApis = true
            wordCount = 0
            valuePos = 0
            methodPos = 0
            producesPos = 0
            api = {url:'', methods:[]}
            method = {method:'', procedure:[]}
        }
        if(isApis && word == '}') {
            isApis = false
            isValue = false
            isMethod = false
            isProduces = false
            api.methods.push(method)
            // console.log(api)
            // apis.push(api)
            mergeApi(apis, api)
        }

        if(word == 'value') isValue = true
        if(word == 'RequestMethod') isMethod = true
        if(word == 'produces=') isProduces = true


        // console.log(word)

        if(isApis) {
            if(word == 'value') isValue = true
            if(word == 'RequestMethod') isMethod = true
            if(word == 'produces=') isProduces = true

            wordCount++
            if(isValue) valuePos++
            if(isMethod) methodPos++
            if(isProduces) producesPos++

            if(valuePos == 3) api.url = word
            if(methodPos == 2) {
                // api.methods.push(word)
                method.method = word.toLowerCase()
                isMethod = false
                methodPos = 0
            }
            if(isProduces && producesPos >= 3) method.procedure.push(word)
        }     
        // let pos = 4
        // if(wordCount == pos) console.log(`${pos} : ${word}`)
    });
    
    const yaml = getText(apis)
    // console.log(util.inspect(apis,true,null))
    // console.log(apis)
    // console.log(yaml)



    fs.writeFileSync(result, yaml)
}


export default documentGetter


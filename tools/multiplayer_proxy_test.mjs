import net from 'node:net';
import fs from 'node:fs/promises';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {createHash} from 'node:crypto';

// Exercise the transport itself with large, fragmented bidirectional streams.
const folder='.gradle/proxy-test';
await fs.mkdir(folder,{recursive:true});
await fs.rm(`${folder}/stop`,{force:true});
const echo=net.createServer(socket=>socket.pipe(socket));
echo.listen(25579,'127.0.0.1'); await once(echo,'listening');
const proxy=spawn(process.execPath,['tools/multiplayer_proxy.mjs','--listen','25578','--target','25579',
  '--latency-ms','80','--jitter-ms','25','--duration','40','--report',`${folder}/report.json`,'--stop-file',`${folder}/stop`],{stdio:['ignore','pipe','inherit']});
try {
  await new Promise((resolve,reject)=>{proxy.stdout.on('data',data=>{if(data.toString().includes('READY'))resolve();});proxy.once('exit',code=>reject(new Error(`Proxy exited early: ${code}`)));});
  const payload=Buffer.alloc(8*1048576);let seed=67;
  for(let i=0;i<payload.length;i++){seed=(Math.imul(seed,1664525)+1013904223)>>>0;payload[i]=seed>>>24;}
  const expected=createHash('sha256').update(payload).digest('hex');
  await Promise.all(Array.from({length:3},async()=>{
    const socket=net.connect({host:'127.0.0.1',port:25578});
    let received=0;const hash=createHash('sha256');
    const complete=new Promise((resolve,reject)=>{
      socket.on('error',reject);socket.setTimeout(30000,()=>reject(new Error('Echo timed out')));
      socket.on('data',data=>{received+=data.length;hash.update(data);if(received===payload.length){
        if(hash.digest('hex')!==expected)reject(new Error('TCP bytes were reordered or changed'));
        else resolve();socket.end();
      }else if(received>payload.length)reject(new Error('Duplicate TCP bytes'));});
      socket.on('end',()=>{if(received!==payload.length)reject(new Error('Truncated TCP stream'));});
    });
    await once(socket,'connect');
    for(let offset=0;offset<payload.length;){const length=Math.min(payload.length-offset,257+(offset%65001));
      if(!socket.write(payload.subarray(offset,offset+length)))await once(socket,'drain');offset+=length;
    }
    await complete;
  }));
  console.log('[PROXYTEST] ALL PASSED: three streams, 24 MiB each direction, identical SHA-256');
} finally {
  await fs.writeFile(`${folder}/stop`,'stop');
  await once(proxy,'exit');echo.close();
}

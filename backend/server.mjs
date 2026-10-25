// Optional backend for a private server. Requires Node.js 20+.
// Set OPENAI_API_KEY and APP_ACCESS_TOKEN as environment secrets.
// Never commit real tokens. Place behind HTTPS reverse proxy with rate limits.
import http from 'node:http';
import { timingSafeEqual } from 'node:crypto';
const port = Number(process.env.PORT || 8787);
const secret = process.env.APP_ACCESS_TOKEN || '';
const apiKey = process.env.OPENAI_API_KEY || '';
if (!secret || !apiKey) throw new Error('Set APP_ACCESS_TOKEN and OPENAI_API_KEY');
const eq = (a,b) => { const x=Buffer.from(a), y=Buffer.from(b); return x.length===y.length && timingSafeEqual(x,y); };
http.createServer(async (req,res) => {
  const respond = (status,data) => { res.writeHead(status,{'Content-Type':'application/json; charset=utf-8','Cache-Control':'no-store'});res.end(JSON.stringify(data)); };
  if (req.method !== 'POST' || req.url !== '/reply') return respond(404,{error:'not found'});
  if (!eq(req.headers.authorization || '', 'Bearer '+secret)) return respond(401,{error:'unauthorized'});
  let raw='';for await(const chunk of req){raw+=chunk;if(raw.length>10000) return respond(413,{error:'too large'});}
  try {
    const {message}=JSON.parse(raw);
    if(typeof message!=='string'||!message.trim()||message.length>2000) return respond(400,{error:'invalid message'});
    const response=await fetch('https://api.openai.com/v1/responses',{
      method:'POST',headers:{'Authorization':'Bearer '+apiKey,'Content-Type':'application/json'},
      body:JSON.stringify({model:process.env.OPENAI_MODEL||'gpt-4.1-mini',
        instructions:'You draft a short, polite reply to a received personal message. Respond in the same language as the incoming message (Bangla, Banglish or English). Never claim to have completed actions, confirmed prices, appointments or payments. Do not invent facts. If unsure, ask a brief clarifying question. Output only the suggested reply.',
        input:message,max_output_tokens:180})
    });
    if(!response.ok) return respond(502,{error:'AI provider unavailable'});
    const data=await response.json();
    const reply=(data.output||[]).flatMap(x=>x.content||[]).filter(x=>x.type==='output_text').map(x=>x.text).join('').trim();
    if(!reply) return respond(502,{error:'empty response'});
    return respond(200,{reply:reply.slice(0,2000)});
  } catch {return respond(500,{error:'request failed'});}
}).listen(port,'127.0.0.1',()=>console.log('AI reply backend listening on loopback',port));

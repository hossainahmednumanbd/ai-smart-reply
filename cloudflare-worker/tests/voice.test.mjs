import test from 'node:test';
import assert from 'node:assert/strict';
import {handleVoice,voiceParts,voiceText,voiceBody} from '../src/voice.js';
const audio={operation:'transcribe',mimeType:'audio/mp4',audio:btoa('test audio')};
const request=body=>new Request('https://example.test/voice',{method:'POST',body:JSON.stringify(body)});
function env(){const counts=new Map();return {GEMINI_API_KEY:'test-only',DB:{prepare(){let bucket;return {bind(value){bucket=value;return this},async first(){const n=counts.get(bucket)||0;if(n>=40)return null;counts.set(bucket,n+1);return {count:n+1};}};}},counts};}
const originalFetch=globalThis.fetch;
let providerCalls=0,providerBody;
globalThis.fetch=async(_url,init)=>{providerCalls++;providerBody=JSON.parse(init.body);return Response.json({candidates:[{finishReason:'STOP',content:{parts:[{thought:true,text:'hidden'},{text:'আমার নাম হোসাইন।'}]}}]});};
try{
 await test('voice preserves input instead of using a short auto-reply prompt',async()=>{
  const e=env();const response=await handleVoice(request(audio),e,'one');
  assert.equal(response.status,200);assert.equal((await response.json()).text,'আমার নাম হোসাইন।');
  assert.equal(providerBody.contents[0].parts[1].inlineData.data,audio.audio);
  assert.match(providerBody.contents[0].parts[0].text,/verbatim/);assert.equal(providerBody.generationConfig.maxOutputTokens,16384);
  assert.ok([...e.counts.keys()][0].endsWith(':voice:one'));
 });
 await test('refinement does not truncate long Bengali input or require user API keys',async()=>{
  const text='বাংলা বক্তব্য '.repeat(800);const parts=voiceParts({operation:'refine',mode:'refine',text});
  assert.ok(parts[0].text.endsWith(text));assert.match(parts[0].text,/Never answer/);
 });
 await test('invalid and oversized input is rejected before spending quota',async()=>{
  const e=env(),before=providerCalls;
  for(const body of [{...audio,audio:'!!=='},{...audio,mimeType:'video/mp4'},{operation:'refine',mode:'bad',text:'text'},{operation:'refine',mode:'refine',text:'x'.repeat(40001)}])assert.equal((await handleVoice(request(body),e,'one')).status,400);
  const huge=new Request('https://example.test/voice',{method:'POST',body:'x'.repeat(6100001)});
  assert.equal((await handleVoice(huge,e,'one')).status,413);
  assert.equal(providerCalls,before);assert.equal(e.counts.size,0);
 });
 await test('voice quota is separate and isolated by Google user',async()=>{
  const e=env();const date=new Date().toISOString().slice(0,10);
  e.counts.set(date+':one',20);e.counts.set(date+':voice:one',39);
  assert.equal((await handleVoice(request(audio),e,'one')).status,200);
  assert.equal((await handleVoice(request(audio),e,'one')).status,429);
  assert.equal((await handleVoice(request(audio),e,'two')).status,200);
  assert.equal(e.counts.get(date+':one'),20);
 });
 await test('incomplete blocked and empty provider results are rejected',()=>{
  for(const result of [{},{candidates:[{finishReason:'MAX_TOKENS',content:{parts:[{text:'half'}]}}]},{candidates:[{finishReason:'STOP',content:{parts:[]}}]}])assert.throws(()=>voiceText(result));
 });
 await test('provider failure exposes no secret and keeps retry possible',async()=>{
  const before=globalThis.fetch;globalThis.fetch=async()=>new Response('private detail',{status:403});
  try{const response=await handleVoice(request(audio),env(),'one');assert.equal(response.status,502);assert.doesNotMatch(await response.text(),/test-only|private detail/);}finally{globalThis.fetch=before;}
 });
}finally{globalThis.fetch=originalFetch;}

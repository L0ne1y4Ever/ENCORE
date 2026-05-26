const http = require('http');

http.get('http://localhost:8080/api/schedules/sch-501/areas', (res) => {
  let data = [];
  res.on('data', (chunk) => {
    data.push(chunk);
  });
  res.on('end', () => {
    const buffer = Buffer.concat(data);
    const text = buffer.toString('utf8');
    console.log('--- RESPONSE TEXT ---');
    console.log(text);
  });
}).on('error', (err) => {
  console.error('Error: ' + err.message);
});

(.venv) PS F:\workspase\TripPamine\TripPamineAI> $port = 18001
(.venv) PS F:\workspase\TripPamine\TripPamineAI> 
(.venv) PS F:\workspase\TripPamine\TripPamineAI> $health = Invoke-RestMethod `
>>     -Uri "http://127.0.0.1:$port/health" `
>>     -Method Get
(.venv) PS F:\workspase\TripPamine\TripPamineAI> 
(.venv) PS F:\workspase\TripPamine\TripPamineAI> $health |
>>     ConvertTo-Json -Depth 10
{
    "status":  "UP",
    "emotion_classifier":  "UP",
    "policy_version":  "q17-locked-margin-0.16-v1",
    "model_version":  "ko42-kc42-rawlogit-0.5-0.5",
    "margin_threshold":  0.16
}
(.venv) PS F:\workspase\TripPamine\TripPamineAI> $body = @{
>>     human_turns = @(                                                                                           
>>         "요즘 취업 준비 때문에 마음이 계속 불안해."                                                            
>>         "결과가 좋지 않을까 봐 잠도 잘 안 와."                                                                 
>>         "부모님 기대까지 생각하면 더 초조해지는 것 같아."                                                      
>>     )                                                                                                          
>> } |                                                                                                            
>>     ConvertTo-Json -Depth 10                                                                                   
(.venv) PS F:\workspase\TripPamine\TripPamineAI> 
(.venv) PS F:\workspase\TripPamine\TripPamineAI> $result = Invoke-RestMethod `
>>     -Uri "http://127.0.0.1:$port/api/v1/emotion/predict" `                                                     
>>     -Method Post `                                                                                             
>>     -ContentType "application/json; charset=utf-8" `                                                           
>>     -Body $body                                                                                                
(.venv) PS F:\workspase\TripPamine\TripPamineAI> 
(.venv) PS F:\workspase\TripPamine\TripPamineAI> $result |
>>     ConvertTo-Json -Depth 10                                                                                   
{
    "fine_label":  "E30",
    "fine_label_name":  "ë¶ ì  ",
    "coarse_label":  "ë¶ ì  ",
    "confidence":  0.6486474871635437,
    "first2_confidence":  0.6486474871635437,
    "first2_margin":  0.49498556554317474,
    "context_mode":  "FIRST2",
    "fallback_triggered":  false,
    "human_turn_count":  3,
    "turns_used":  2,
    "policy_version":  "q17-locked-margin-0.16-v1",
    "model_version":  "ko42-kc42-rawlogit-0.5-0.5",
    "latency_ms":  118.50289998983499
}
> 
> 
> (.venv) PS F:\workspase\TripPamine\TripPamineAI> netsh interface ipv6 show excludedportrange protocol=tcp

프로토콜 tcp 포트 제외 범위

시작 포트    끝 포트      
----------    --------      
      5357        5357      
      5537        5636      
      7468        7567      
      7768        7867      
      7868        7967      
      7975        8074      
      8552        8651      
     50000       50059     *

* - 관리 포트 제외입니다.

(.venv) PS F:\workspase\TripPamine\TripPamineAI> $port = @'
>> import socket                                                                                                  
>>                                                                                                                
>> for port in range(18001, 18101):                                                                               
>>     sock = socket.socket(                                                                                      
>>         socket.AF_INET,                                                                                        
>>         socket.SOCK_STREAM,                                                                                    
>>     )                                                                                                          
>>                                                                                                                
>>     try:                                                                                                       
>>         sock.bind(                                                                                             
>>             ("127.0.0.1", port)                                                                                
>>         )                                                                                                      
>>     except OSError:                                                                                            
>>         sock.close()                                                                                           
>>         continue                                                                                               
>>                                                                                                                
>>     sock.close()                                                                                               
>>                                                                                                                
>>     print(port)                                                                                                
>>     break                                                                                                      
>> else:                                                                                                          
>>     raise SystemExit(                                                                                          
>>         "No bindable port found."                                                                              
>>     )                                                                                                          
>> '@ |                                                                                                           
>>     .\.venv\Scripts\python.exe -                                                                               
(.venv) PS F:\workspase\TripPamine\TripPamineAI> 
(.venv) PS F:\workspase\TripPamine\TripPamineAI> $port = [int]$port.Trim()
(.venv) PS F:\workspase\TripPamine\TripPamineAI> 
(.venv) PS F:\workspase\TripPamine\TripPamineAI> Write-Host ""

(.venv) PS F:\workspase\TripPamine\TripPamineAI> Write-Host "SELECTED PORT =" $port
SELECTED PORT = 18001
(.venv) PS F:\workspase\TripPamine\TripPamineAI> $env:TRIPPAMINE_AI_DEVICE = "cuda"
(.venv) PS F:\workspase\TripPamine\TripPamineAI> 
(.venv) PS F:\workspase\TripPamine\TripPamineAI> .\.venv\Scripts\python.exe `
>>     -m uvicorn `                                                                                               
>>     trippamine_ai.api.app:app `                                                                                
>>     --host 127.0.0.1 `                                                                                         
>>     --port $port                                                                                               
INFO:     Started server process [20220]
INFO:     Waiting for application startup.
Loading weights: 100%|███████████████████████████████████████████████████████| 203/203 [00:00<00:00, 6489.56it/s]
Loading weights: 100%|███████████████████████████████████████████████████████| 203/203 [00:00<00:00, 6427.35it/s]
INFO:     Application startup complete.
INFO:     Uvicorn running on http://127.0.0.1:18001 (Press CTRL+C to quit)
INFO:     127.0.0.1:6161 - "GET /health HTTP/1.1" 200 OK
INFO:     127.0.0.1:6169 - "POST /api/v1/emotion/predict HTTP/1.1" 200 OK
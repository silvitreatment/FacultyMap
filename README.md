# FacultyMap 

Карта ПМ-ПУ в вэб версии, только нормальная. 

## Работа с аудиториями

```bash
curl -X POST http://localhost:8080/api/v1/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"значение ADMIN_PASSWORD"}'
```


```bash
curl -X POST http://localhost:8080/api/v1/admin/rooms \
  -H 'Authorization: Bearer <token>' \
  -H 'Content-Type: application/json' \
  -d '{"floorId":1,"number":"101","name":"Лекционная аудитория","description":"","x":10,"y":20,"width":100,"height":80}'
curl -X POST http://localhost:8080/api/v1/admin/rooms/1/publish \
  -H 'Authorization: Bearer <token>'
```

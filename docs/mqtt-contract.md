## Tollbooth IDs

I seguenti `tollboothId` sono gli identificativi univoci dei caselli gestiti dal sistema:

- VC_Est
- VC_Ovest
- AT_Est
- AT_Ovest
- MI_Est
- MI_Ovest

Regola: `tollboothId` è sempre una stringa e compare sempre come secondo livello del topic `highway/{tollboothId}/...`.


## Topic tree

### Eventi generati dal sottosistema casello (publisher: `toll`)
- `highway/{tollboothId}/entry/manual/events`
- `highway/{tollboothId}/entry/telepass/events`
- `highway/{tollboothId}/exit/manual/events`
- `highway/{tollboothId}/exit/telepass/events`

### Comandi generati dal simulatore guidatore (publisher: `user`)
- `highway/{tollboothId}/entry/manual/commands`
- `highway/{tollboothId}/entry/telepass/commands`
- `highway/{tollboothId}/exit/manual/commands`
- `highway/{tollboothId}/exit/telepass/commands`

### Risposte (publisher: `toll` oppure `camera` a seconda del caso)
- `highway/{tollboothId}/entry/manual/responses`
- `highway/{tollboothId}/entry/telepass/responses`
- `highway/{tollboothId}/exit/manual/responses`
- `highway/{tollboothId}/exit/telepass/responses`

### Richieste di calcolo pedaggio (publisher: `toll`, subscriber: `server`)
- `highway/requests/tollprice`

### Stato uscita manuale (publisher: `toll`, subscriber: `user`)
- `highway/{tollboothId}/exit/manual/state`

### Camera (richieste immagine/riconoscimento) (publisher: `toll`, subscriber: `camera`)
- `highway/{tollboothId}/entry/camera/requests`
- `highway/{tollboothId}/exit/camera/requests`


## Message conventions (common fields)

I payload JSON sono volutamente minimali:

- `timestamp` (string, required): ISO-8601 UTC (es. "2026-01-24T18:30:00Z")
- `type` (string, required): tipo messaggio/evento
- `correlationId` (string, optional): solo per flussi request/response
- `replyTopic` (string, optional): solo nelle request globali, indica dove rispondere

Regola anti-ridondanza:
- `tollboothId` NON è nel payload se è già nel topic `highway/{tollboothId}/...`
- si ricava dal topic nel consumer.
- Nei messaggi di uscita (.../exit/.../events) entryTollboothId è obbligatorio perché l’ingresso non è deducibile dal topic di uscita.

## Correlation rules

- `correlationId`: UUID per correlare una risposta a una richiesta (obbligatorio nei flussi request/response).
- Regola: ogni richiesta genera esattamente una risposta con lo stesso `correlationId`.
- Timeout logico: se una risposta non arriva entro X secondi (valore definito a livello implementativo), il chiamante può ripetere la richiesta con nuovo `correlationId`.
- Per ENTRY_ACCEPTED e EXIT_COMPLETED il canale (manual/telepass) si deduce dal topic.

## 4.1 Comandi user → toll (entry/exit)

### Command Manual entry

- Topic: `highway/{tollboothId}/entry/manual/commands`

- Publisher: `user`
- Subscribers: `toll`
- Purpose: richiesta ingresso in modalità manuale (ritiro ticket)

Payload:
- timestamp (required)
- type = `"ENTRY_MANUAL_COMMAND"` (required)
- plate (optional, ignored by toll logic because plate is read by camera)

Example:
```json
{
  "timestamp": "2026-01-24T18:30:00Z",
  "type": "ENTRY_MANUAL_COMMAND",
  "plate": "AB123CD"
}
```

### Command Telepass entry

- Topic: `highway/{tollboothId}/entry/telepass/commands`

- Publisher: `user`
- Subscribers: `toll`
- Purpose: richiesta ingresso telepass

Schema:
- timestamp (required)
- type = `"ENTRY_TELEPASS_COMMAND"` (required)
- telepassId (required)
- plate (optional, ignored by toll logic because plate is read by camera)

Example:
```json
{
  "timestamp": "2026-01-24T18:31:00Z",
  "type": "ENTRY_TELEPASS_COMMAND",
  "plate": "AB123CD",
  "telepassId": "TP-000045"
}
```

Compatibilità:
- sui topic di ingresso è accettato anche `type = "REQUEST_ENTRY"` (legacy).
- sui topic di uscita è accettato anche `type = "REQUEST_EXIT"` (legacy).

### Command Manual exit

- Topic: `highway/{tollboothId}/exit/manual/commands`

- Publisher: `user`
- Subscribers: `toll`
- Purpose: richiesta uscita consegnando ticket

Schema:
- timestamp (required)
- type = "EXIT_MANUAL_COMMAND" (required)
- ticketId (string)

Example:
```json
{
  "timestamp": "2026-01-24T19:05:00Z",
  "type": "EXIT_MANUAL_COMMAND",
  "ticketId": "TCK-9F2A3B"
}
```

### Command Telepass exit

- Topic: `highway/{tollboothId}/exit/telepass/commands`

- Publisher: `user`
- Subscribers: `toll`
- Purpose: richiesta uscita telepass

Schema:
- timestamp (string, required)
- type = "EXIT_TELEPASS_COMMAND" (required)
- telepassId (string, required)

Example:
```json
{
  "timestamp": "2026-01-24T19:07:00Z",
  "type": "EXIT_TELEPASS_COMMAND",
  "telepassId": "TP-000045"
}
```

## 4.2 Eventi toll → server (entry/exit events)

Qui l’idea: il sottosistema `toll` pubblica **eventi** (es. veicolo entrato/uscito) e il backend `server` li salva su DB.

### Event entry manual

- Topic: `highway/{tollboothId}/entry/manual/events`

- Publisher: `toll`
- Subscribers: `server`
- Purpose: conferma ingresso manuale con ticket generato

Schema:
- timestamp (string, required)
- type = "ENTRY_ACCEPTED" (required)
- plate (string, required)
- ticketId (string, required)

Example:
```json
{
  "timestamp": "2026-01-24T18:30:03Z",
  "type": "ENTRY_ACCEPTED",
  "plate": "AB123CD",
  "ticketId": "TCK-9F2A3B"
}
```

### Event entry telepass

- Topic: `highway/{tollboothId}/entry/telepass/events`

- Publisher: `toll`
- Subscribers: `server`
- Purpose: conferma ingresso telepass

Schema:
- timestamp (string, required)
- type = "ENTRY_ACCEPTED" (required)
- plate (string, required)
- telepassId (string, required)

Example:
```json
{
  "timestamp": "2026-01-24T18:31:02Z",
  "type": "ENTRY_ACCEPTED",
  "plate": "AB123CD",
  "telepassId": "TP-000045"
}
```

### Event exit manual

- Topic `highway/{tollboothId}/exit/manual/events`

- Publisher: `toll`
- Subscribers: `server`
- Purpose: uscita manuale completata (pagamento sul posto)

Schema:
- timestamp (string, required)
- type = "EXIT_COMPLETED" (required)
- entryTollboothId (required)
- ticketId (string, required)
- amountCents (integer, required)

Example:
```json
{
  "timestamp": "2026-01-24T19:05:10Z",
  "type": "EXIT_COMPLETED",
  "entryTollboothId": "VC_Est",
  "ticketId": "TCK-9F2A3B",
  "amountCents": 720
}
```

### Event exit telepass

- Topic: `highway/{tollboothId}/exit/telepass/events`

- Publisher: `toll`
- Subscribers: `server`
- Purpose: uscita telepass (debito da riscuotere/registrare)
- Nota: prima della pubblicazione di `EXIT_COMPLETED`, il dispositivo di uscita telepass richiede la lettura targa alla camera sul canale `exit`.
- Nota: su `EXIT_COMPLETED` in modalità telepass il backend registra un debito telepass associato a `telepassId` e `amountCents` (debito da riscuotere lato gestionale).

Schema:
- timestamp (string, required)
- type = "EXIT_COMPLETED" (required)
- entryTollboothId (required)
- telepassId (string, required)
- amountCents (integer, required)
- plate (string, optional)

Example:
```json
{
  "timestamp": "2026-01-24T19:07:08Z",
  "type": "EXIT_COMPLETED",
  "entryTollboothId": "VC_Est",
  "telepassId": "TP-000045",
  "amountCents": 720,
  "plate": "AB123CD"
}
```

## 4.3 Richiesta pedaggio toll → server e risposta server → toll

### Request tollprice

- Topic: `highway/requests/tollprice`

- Publisher: `toll`
- Subscribers: `server`
- Purpose: richiesta calcolo pedaggio tra due caselli

Schema:
- timestamp (string, required)
- type = "TOLLPRICE_REQUEST" (required)
- correlationId (string, required)
- replyTopic (required)
- entryTollboothId (string, optional)
- exitTollboothId (string, required)
- ticketId (string, optional)
- telepassId (string, optional)

Regola:
- almeno uno tra `entryTollboothId`, `ticketId`, `telepassId` deve essere presente.
- se manca `entryTollboothId`, il server lo risolve da viaggio attivo cercando `ticketId` o `telepassId`.

Example:
```json
{
  "timestamp": "2026-01-24T19:05:01Z",
  "type": "TOLLPRICE_REQUEST",
  "correlationId": "c6f1df7b-3d5e-4d1c-bb11-8c8e45fb4cc7",
  "replyTopic": "highway/MI_Ovest/exit/manual/responses",
  "entryTollboothId": "VC_Est",
  "exitTollboothId": "MI_Ovest",
  "ticketId": "TCK-9F2A3B"
}
```

### Response tollprice

- Topic: pubblicato su replyTopic indicato nella request

- Publisher: `server`
- Subscribers: `toll`
- Purpose: richiesta calcolo pedaggio

Schema:
- timestamp (string, required)
- type = "TOLLPRICE_RESPONSE" (required)
- correlationId (string, required)
- amountCents (required)
- currency = "EUR" (required)
- entryTollboothId (string, optional)
- plate (string, optional)
- entryAt (string ISO-8601, optional)

Example:
```json
{
  "timestamp": "2026-01-24T19:05:02Z",
  "type": "TOLLPRICE_RESPONSE",
  "correlationId": "c6f1df7b-3d5e-4d1c-bb11-8c8e45fb4cc7",
  "entryTollboothId": "VC_Est",
  "plate": "AB123CD",
  "entryAt": "2026-01-24T18:30:03Z",
  "amountCents": 720,
  "currency": "EUR"
}
```

**Regola risposta**: il server risponde sul topic di uscita coerente col canale (manual/telepass):

- `highway/{exitTollboothId}/exit/manual/responses`
- `highway/{exitTollboothId}/exit/telepass/responses`

## 4.4 Camera requests toll → camera e camera response → toll

### Camera requests

- Topic: `highway/{tollboothId}/{entry|exit}/camera/requests`

- Publisher: `toll`
- Subscribers: `camera`
- Purpose: richiesta acquisizione targa (simulata)

Schema:
- timestamp (string, required)
- type = "CAMERA_REQUEST" oppure "CAMERA_PLATE_REQUEST" (required)
- correlationId (string, required)
- channel (string, optional: "manual" | "telepass")
- passId (string, optional: ticketId/telepassId associato alla richiesta)

Regole:
- `direction` è ricavata dal topic (entry/exit), non dal payload.
- se `channel` non è presente, `camera-service` prova a inferirlo da `passId`
  (`TCK-*` -> manual, altrimenti telepass).

Example:
```json
{
  "timestamp": "2026-01-24T18:29:59Z",
  "type": "CAMERA_REQUEST",
  "correlationId": "e1c0f2f1-03c2-4e2b-8d5d-6aab6a1ff3b2",
  "channel": "manual",
  "passId": "TCK-9F2A3B"
}
```

### Camera response

- Topic:`highway/{tollboothId}/{entry|exit}/{manual|telepass}/responses`

- Publisher: `camera`
- Subscribers: `toll`
- Purpose: risultato riconoscimento targa

Schema:
- timestamp (string, required)
- type = "CAMERA_RESPONSE" (required)
- correlationId (string, required)
- plate (string, required)
- confidence (number, required)
- passId (string, optional, legacy compatibility)
- direction (string, optional, legacy compatibility)

Example:
```json
{
  "timestamp": "2026-01-24T18:30:00Z",
  "type": "CAMERA_RESPONSE",
  "correlationId": "e1c0f2f1-03c2-4e2b-8d5d-6aab6a1ff3b2",
  "plate": "AB123CD",
  "confidence": 0.97,
  "passId": "TP-000045",
  "direction": "exit"
}
```

## 4.5 Stato uscita manuale toll → user

### State tollbooth manual exit

- Topic: `highway/{tollboothId}/exit/manual/state`

- Publisher: `toll`
- Subscribers: `user`
- Purpose: aggiornamento stato barriera/pagamento (manual)

Schema:
- timestamp (string, required)
- type (string, required) uno tra:
  - "EXIT_PENDING_PRICE"
  - "REQUEST_PAYMENT"
  - "PAYMENT_REJECTED"
  - "PAYMENT_ACCEPTED"
  - "EXIT_REJECTED"
- ticketId (string, opzionale in base al type)
- amountCents / amountCentsDue (integer, opzionali in base al type)
- currency (string, opzionale)
- entryTollboothId, plate, entryAt (opzionali su REQUEST_PAYMENT)
- reason (string, opzionale su messaggi rejected)

Example:
```json
{
  "timestamp": "2026-01-24T19:05:12Z",
  "type": "REQUEST_PAYMENT",
  "ticketId": "TCK-9F2A3B",
  "entryTollboothId": "VC_Est",
  "plate": "AB123CD",
  "entryAt": "2026-01-24T18:30:03Z",
  "amountCents": 720,
  "currency": "EUR"
}
```

### State tollbooth telepass exit

- Topic: `highway/{tollboothId}/exit/telepass/state`
- Publisher: `toll`
- Subscribers: `user` (UI/simulator)
- Purpose: aggiornamento avanzamento uscita telepass

Schema:
- timestamp (string, required)
- type (string, required) uno tra:
  - "EXIT_PENDING_VALIDATION"
  - "EXIT_COMPLETED_UI"
  - "EXIT_REJECTED"
- telepassId (string, opzionale in base al type)
- amountCents, currency, plate (opzionali su EXIT_COMPLETED_UI)
- reason (string, opzionale su EXIT_REJECTED)

# 5 ACL mapping (utente → read/write)

## ACL mapping

### admin
- read: `#`
- write: `#`

### toll
- write: `highway/+/entry/manual/events`
- write: `highway/+/entry/telepass/events`
- write: `highway/+/exit/manual/events`
- write: `highway/+/exit/telepass/events`
- write: `highway/+/exit/manual/state`
- write: `highway/requests/tollprice`
- write: `highway/+/entry/camera/requests`
- write: `highway/+/exit/camera/requests`
- read: `highway/+/entry/manual/commands`
- read: `highway/+/entry/telepass/commands`
- read: `highway/+/exit/manual/commands`
- read: `highway/+/exit/telepass/commands`
- read: `highway/+/entry/manual/responses`
- read: `highway/+/entry/telepass/responses`
- read: `highway/+/exit/manual/responses`
- read: `highway/+/exit/telepass/responses`

### camera
- read:  `highway/+/entry/camera/requests`
- read:  `highway/+/exit/camera/requests`
- write: `highway/+/entry/manual/responses`
- write: `highway/+/exit/manual/responses`
- write: `highway/+/entry/telepass/responses` 
- write: `highway/+/exit/telepass/responses`

### user
- write: `highway/+/entry/manual/commands`
- write: `highway/+/entry/telepass/commands`
- write: `highway/+/exit/manual/commands`
- write: `highway/+/exit/telepass/commands`
- read:  `highway/+/exit/manual/state`

### server (backend)
- read  `highway/+/entry/manual/events`
- read  `highway/+/entry/telepass/events`
- read  `highway/+/exit/manual/events`
- read  `highway/+/exit/telepass/events`
- read  `highway/requests/tollprice`

- write `highway/+/exit/manual/responses`
- write `highway/+/exit/telepass/responses`
- write `highway/+/entry/manual/responses`
- write `highway/+/entry/telepass/responses`

## Backend processing rules (server)

- On ENTRY_ACCEPTED (manual): create active trip linked to ticketId with entry tollboothId from topic.
- On ENTRY_ACCEPTED (telepass): create active trip linked to telepassId with entry tollboothId from topic.
- On EXIT_COMPLETED (manual): close trip located by ticketId, store entryTollboothId from payload and exit tollboothId from topic, mark paid=true.
- On EXIT_COMPLETED (telepass): close trip located by telepassId, store entryTollboothId from payload and exit tollboothId from topic, create telepass debt (open).
- On tollprice request: resolve entry tollbooth from request or active trip (ticketId/telepassId), lookup fare by (entryTollboothId, exitTollboothId), publish TOLLPRICE_RESPONSE to replyTopic with amount and trip details (when available)

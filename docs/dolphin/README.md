# Mobilepad no Dolphin (controle de GameCube)

Perfis prontos para a porta 1 do GameCube. Copie os `.ini` para
`%APPDATA%\Dolphin Emulator\Config\Profiles\GCPad\` e carregue em
**Controles → Porta 1 → Configurar → Perfil**.

| Perfil | Quando usar | Dispositivo |
|---|---|---|
| **Mobilepad (USB e Wi-Fi)** | celular ligado ao companion (cabo ou Wi-Fi) | `XInput/0/Gamepad` (controle Xbox virtual do companion) |
| **Mobilepad Bluetooth** | celular pareado direto com o PC, modo "PC · Android · TV" | `SDL/0/Mobilepad` |

Os dois mantêm as posições do controle de GameCube: botão de baixo = A, esquerda = B, direita = X, cima = Y; RT = R (drift no Mario Kart), LT/LB = L (item), RB = Z.

## Bluetooth: ensinar o SDL a reconhecer o Mobilepad

O Windows mostra o Mobilepad do Bluetooth como um joystick genérico. Para o SDL (e o Dolphin) tratá-lo como gamepad, crie a variável de usuário (uma vez só, e reabra o Dolphin):

```
setx SDL_GAMECONTROLLERCONFIG "0300c2a1750000000001000000000000,Mobilepad,a:b0,b:b1,x:b3,y:b4,leftshoulder:b6,rightshoulder:b7,back:b10,start:b11,guide:b12,leftstick:b13,rightstick:b14,dpup:h0.1,dpright:h0.2,dpdown:h0.4,dpleft:h0.8,leftx:a0,lefty:a1,rightx:a2,righty:a3,lefttrigger:a4,righttrigger:a5,platform:Windows,"
```

O perfil Bluetooth funciona com ou sem essa variável: cada função aceita o nome de gamepad (`Button S`…) ou o número cru (`Button 0`…).

## Latência medida (S25 → notebook)

USB ~6 ms (P99 11 ms); Bluetooth ~11 ms (P99 ~31 ms). Detalhes em `labs/AUDITORIA.md`.

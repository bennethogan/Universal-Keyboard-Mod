### 🌐 Read this in other languages: 
### [English](README.md) | [Português](languages/portuguese/README.pt.md) | [Русский](languages/russian/README.ru.md) |


# NOTAS IMPORTANTES 

### CC:Tweaked e Create não são necessários, mas é altamente recomendável ter ambos instalados para que possa aproveitar todas as features que eles oferecem em conjunto com o Universal Keyboard. Muitas das features não relacionadas a redstone dos teclados utilizam os periféricos do CC:Tweaked. Você precisará baixar a ultima versão do CC:Tweaked pelo modrinth, e não pelo Curseforge.

### Se você tiver o <span style="color:red">Create: Connected</span> instalado-- então os links de redstone sem-fio NÃO VÃO FUNCIONAR ATÉ você trocar a opção "Redstone Link Wildcard" para "False" nas configurações de mod. Siga o esquema a seguir para desabilitar essa função: Create: Connected --> Common --> Features --> "Redstone Link Wildcard" --> "False" or "X"

-------------------

# As features mais legais dos teclados
### > 'Live controls' para links de redstone sem-fio, e propulsores do Create Propulsion's (através dos periféricos do CC:Tweaked's)
### > 'Sequenciador' ferramenta que pode executar scripts simples que interagem com a redstone sem-fio e periféricos do CC:Tweaked (e também com live controls se você usar 2 teclados!)

---------------------
# Outras features do teclado
### > Importar atalhos de teclados de um Aeronautics Typewriter colocado até 20 blocos de distância, para facilmente agilizar as suas assosciações de tecla!
### > Mude remotamente os valores de painel do mod Create, como as configurações de RPM de um "Speed Controller"
### > Compatibilidades com o Sable --> Enquanto estiver em um sub-level, o teclado saberá a sua velocidade e posição no mundo. Existem scripts PID no Github que você pode carregar no seu teclado para testar!
### > 'Thruster Control Panel', podem parecer um pouco inúteis agora que eu adicionei os 'Live Controls', mas eu vou manté-lo
### > Selecione diretamente valores nos seus periféricos do CC:Tweaked, e digite diretamente para computadores (porque no fim das contas, é um teclado!)!
### > Você pode colocar um display link ao lado do teclado. Não fica bonito, mas você poderá ver muitas informações se você conectar o teclado a um periférico do CC:Tweaked!


### Veja os slides abaixo para instruções mais detalhadas (incompleto)

<img width="960" height="540" alt="UniversalKeyboardWiki_portuguese" src="https://github.com/user-attachments/assets/45d5e536-d7b6-412c-b18b-b00fb39af408" />

<img width="960" height="540" alt="UniversalKeyboardWiki_portuguese(1)" src="https://github.com/user-attachments/assets/24e5827c-d5da-4284-8182-fee38364bba4" />

<img width="960" height="540" alt="UniversalKeyboardWiki_portuguese(2)" src="https://github.com/user-attachments/assets/816fdd52-187a-43b9-89bd-0fce97a242d7" />

<img width="960" height="540" alt="UniversalKeyboardWiki_portuguese(3)" src="https://github.com/user-attachments/assets/7b558f09-efb9-407f-8035-6c77bd2dd779" />

<img width="960" height="540" alt="UniversalKeyboardWiki_portuguese(4)" src="https://github.com/user-attachments/assets/b37fbc7b-4e36-4ab0-917a-cca63ec91448" />

<img width="960" height="540" alt="UniversalKeyboardWiki_portuguese(5)" src="https://github.com/user-attachments/assets/2e97a368-0229-4161-8091-6e1a45a66861" />



-----------

## Quais features opicionais dependem(ou não) de mods extras?

| Feature | Keyboard Mod Only | +CC:Tweaked | +Create | +CC+Create | +Create+Aero/Propulsion | +CC+Create+Aero/Propulsion |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Live Controller — local RS outputs** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Live Controller — Wireless RS links** | x | x | ✓ | ✓ | ✓ | ✓ |
| **Live Controller — Thruster power/vector** | x | x | x | x | x | ✓ |
| **Typewriter import** | x | x | x | x | ✓ | ✓ |
| **CC Computer mode** | x | ✓ | x | ✓ | x | ✓ |
| **CC Peripheral mode** | x | ✓ | x | ✓ | x | ✓ |
| **Value Panel** | x | x | ✓ | ✓ | ✓ | ✓ |
| **Thruster Control** | x | x | x | x | x | ✓ |
||
| **Sequencer — Set RS Out / Delay / Jump / Math / Loop / End** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Sequencer — If / Skip** (RS inputs only, without CC) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Sequencer — Wait For** (RS inputs only, without CC) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| **Sequencer — Set Value** (CC peripheral setter) | x | ✓ | x | ✓ | x | ✓ |
| **Sequencer — Type Text / Type Var** | x | ✓* | x | ✓* | x | ✓* |
| **Sequencer — Sublevel stats as condition/getter** | x | x | x | x | ✓ | ✓ |


*Type Text/Var additionally require a CC computer to be linked on that channel.

---------------------------------------------

## BUGS CONHECIDOS

### > O "creative vector thruster" não deixa editar o "ThrustPower". Isso é diferente de um sinal "on". É o valor em "%"(porcentagem) que é mostrado nos "Creative thrusters". Isso funciona no "Creative thruster", mas não no "Creative Vector Thruster".

### > "Multiblock Creative thruster", que é a nova feature do Create: Propulsion, não está funcionou para nós em um dos testes, mas funcionou em outro. Era a única coisa a qual não funcionava, e como se trata de uma nova feature do Propulsion, Eu vou deixar isso um pouco de lado antes de tentar resolver esse bug

### > Por favor nos conte caso encontre mais bugs. Agradecemos a sua compreensão!

-------------------------------------------------------------------

## E também há craftings legais se você tiver o Create baixado, mas o Create não é obrigatório:

<img width="1920" height="1123" alt="2026-05-09_20 07 39" src="https://github.com/user-attachments/assets/5d87b6bf-7f4a-4694-aa9c-6661a1ca42e4" />
<img width="854" height="480" alt="2026-05-09_20 09 18" src="https://github.com/user-attachments/assets/c7805fea-ca2c-4101-a859-1334b9ca3a1d" />
<img width="1920" height="1123" alt="2026-05-16_20 52 29" src="https://github.com/user-attachments/assets/2b9865ba-aa8c-45a2-8558-109682f20309" />










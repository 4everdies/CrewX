package myau.module.modules;

import myau.module.Module;
import myau.events.UpdateEvent;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class Insults extends Module {

    public final ModeProperty chatMode = new ModeProperty("chat-mode", 0, new String[]{"NONE", "/G"});

    private final Map<EntityLivingBase, Long> attackedEntities = new HashMap<>();
    private final Random random = new Random();

    private final String[] insults = {
        "Até o Oliver Tree teve mais sucesso que o %s #CCW",
        "Eu lhe amaldiçoo %s #CCW",
        "Arrombarei arrombaras %s #CCW",
        "%s: Staff staff bane esse rack aq meo #CCW",
        "%s titio epstein tá orgulhoso #CCW",
        "%s joga lá no forum e fala que tu foi strp4d0 pela crackcrew #CCW",
        "%s Doxei explanei farmei clipa tropa!!!!!! #CCW",
        "%s cuidado com os hackers russos #CCW",
        "%s eae personagem não desbloqueado #CCW",
        "%s Sai dae estátua da obesidade #CCW",
        "deus te elimine %s, amém. #CCW",
        "%s falar \"mds\" não vai te ajudar #CCW",
        "%s diria para você melhorar mas isso é impossível #CCW",
        "Operação no rio de janeiro não deu tão certo... Acabei de matar outro favelado %s #CCW",
        "Até pessoas de esquerda são mais inteligentes que você %s #CCW",
        "Você é ruim %s #CCW",
        "Até o di4b0 treme te vendo jogar %s #CCW",
        "ChatGPT me informe uma pessoa pior que o %s #CCW",
        "O bullying não foi evitado no seu caso %s #CCW",
        "Nao sabia que o %s tinha um recorde mundial na speedrun de perder o pai #CCW",
        "Tranque sua porta antes que eu c4gu3 no seu tapete %s! #CCW",
        "%s, sempre soube que você tinha medo de uma carteira de trabalho #CCW",
        "Haha! estou te motivando a se assumir pra sua familia, vc consegue... %s #CCW",
        "Sabe %s, as vezes eu me pergunto se burrice tem limite #CCW",
        "Uma vez %s disse, 1 real ou uma derrota misteriosa? #CCW",
        "Tengo lengo tengo... canta comigo %s #CCW",
        "Mas eaí %s, é pavê ou é pra comer? #CCW",
        "%s, na sua idade eu tava trabalhando de servente de pedreiro #CCW",
        "%s jogador de free fire no Minecraft é f0d4 #CCW",
        "%s, seus cabelo é dahora, seu corpão violão! #CCW",
        "%s É FEMBOY! #CCW",
        "OLHA LA %s, É A CLEIDE SEM CALCINHA! #CCW",
        "%s, eu sei que vc nao é a cleidê mas use um vestido transparente! #CCW",
        "0 - 20000 = burrice do %s #CCW",
        "OLHA %s, O STEVE SAPECANDO O BLOCO, fd5 #CCW",
        "o %s é sabado mesmo. #CCW",
        "P.Diddy >>>> %s #CCW",
        "Um aleijado joga melhor que você %s e isso é um fato. #CCW",
        "%s, a sua mãe é tão grd4 que as pessoas fazem cooper em volta dela! #CCW",
        "%s a sua mãe é tão grd4 que quando ela foi ao cinema, sentou perto de todo mundo! #CCW",
        "%s a sua mãe é tão grd4 que ela foi batizada num oceano! #CCW",
        "%s a sua mãe é tão burra que ela foi atropelada por um carro estacionado! #CCW",
        "%s a sua mãe é tão feia que ela transformou a Medusa em pedra! #CCW",
        "%s a sua mãe é tão velha que ela deve a Jesus 3 pratas! #CCW",
        "%s a sua mãe é tão grd4 que ela tem que comprar duas passagens aéreas! #CCW",
        "%s, ainda me pergunto como você conseguiu baixar esse jogo #CCW",
        "%s, essa torta aí... ela é torta ou é reta? #CCW",
        "%s, venha usar CrewX client e levar flag hoje mesmo! #CCW",
        "%s, uma vez elon musk disse, eu vou dominar marte! #CCW",
        "%s luta pior que um goblin bêbado! #CCW",
        "a espada do %s deve ser feita de papelão, só pode! #CCW",
        "%s, sabia que uma certa overlay custa 90 reais e é ruim? #CCW",
        "%s, a nerd overlay é a melhor! cuidado que eu tenho mais de 6 de reach! #CCW",
        "%s, eu amo comprar coisas por 90 reais, feitas em JS e que são ruins! #CCW",
        "%s! Sabe a diferença entre vc e uma ameba? NENHUMA! #CCW",
        "%s! por favor não reporte os bugs no prediction do CRIS-AC #CCW",
        "%s, eu sei como é dificil passar por um slab e levar flag :( #CCW",
        "%s, CUIDADO! O CRIS ANTICHEAT VAI SER DESABILITADO PELA POT DE JUMP! #CCW",
        "%s... eu sou seu pai. #CCW"
    };

    public Insults() {
        super("Insults", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() != EventType.PRE) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        Iterator<Map.Entry<EntityLivingBase, Long>> iterator = attackedEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<EntityLivingBase, Long> entry = iterator.next();
            EntityLivingBase entity = entry.getKey();
            long time = entry.getValue();

            if (System.currentTimeMillis() - time > 8000L) {
                iterator.remove();
                continue;
            }

            if (entity.isDead || entity.getHealth() <= 0.0F) {
                if (entity instanceof EntityPlayer) {
                    sendInsult(entity.getName());
                }
                iterator.remove();
                continue;
            }

            if (!mc.theWorld.loadedEntityList.contains(entity)) {
                iterator.remove();
            }
        }

        if (mc.thePlayer.isSwingInProgress && mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null) {
            if (mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
                EntityLivingBase target = (EntityLivingBase) mc.objectMouseOver.entityHit;

                if (target instanceof EntityPlayer && target != mc.thePlayer) {
                    attackedEntities.put(target, System.currentTimeMillis());
                }
            }
        }
    }

    private void sendInsult(String target) {
        String insult = insults[random.nextInt(insults.length)];
        String finalMessage = String.format(insult, target);

        if (this.chatMode.getValue() == 1) {
            finalMessage = "/g " + finalMessage;
        }

        Minecraft.getMinecraft().thePlayer.sendChatMessage(finalMessage);
    }

    @Override
    public void onEnabled() {
        attackedEntities.clear();
        super.onEnabled();
    }

    @Override
    public void onDisabled() {
        attackedEntities.clear();
        super.onDisabled();
    }
}
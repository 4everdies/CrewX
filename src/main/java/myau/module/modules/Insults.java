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

    public final ModeProperty chatMode = new ModeProperty("chat-mode", 0, new String[]{"Normal", "/G", "/TELL"});

    private final Map<EntityLivingBase, Long> attackedEntities = new HashMap<>();
    private final Random random = new Random();

    private final String[] insults = {
        "Até o Oliver Tree teve mais sucesso que o %s #CCW",
        "Eu lhe amaldiçoo %s #CCW",
        "Arrombarei arrombaras %s #CCW",
        "%s joga lá no fórum e fala que tu foi strp4d0 pela crackcrew #CCW",
        "%s falar \"mds\" não vai te ajudar #CCW",
        "%s diria para você melhorar mas isso é impossível #CCW",
        "ChatGPT me informe uma pessoa pior que o %s #CCW",
        "O bullying não foi evitado no seu caso %s #CCW",
        "Haha! estou te motivando a se assumir pra sua família, você consegue... %s #CCW",
        "Sabe %s, às vezes eu me pergunto se burrice tem limite #CCW",
        "0 - 20000 = burrice do %s #CCW",
        "%s, a nerd overlay é a melhor! cuidado que eu tenho mais de 6 de reach! #CCW",
        "%s sua mãe é tão g0rd4 que quando ela anda ela muda de DDD #CCW",
        "Seu pai bêbado vai chegar em 1h, se esconda %s #CCW",
        "%s CrewX client no topo, você no lix0 gg|crackcrew #CCW",
        "Até a Argentina fez alguma coisa nesse jogo e você não, %s #CCW",
        "-1000 de aura pra você %s #CCW",
        "A STAFF VAI ME BANIR E EU VOLTAREI EM 5 SEGUNDOS HAHAHAHAH %s DESISTA #CCW",
        "Cansado de perder pra cheaters? %s entre agora mesmo gg|crackcrew #CCW",
        "%s meu cheat é melhor que o OWL client, entre já gg|crackcrew #CCW",
        "Ei %s, sabia que a ex-staff alaninha do Hylex na verdade não é mulher, e aliás ela gosta de menores de idade também... #CCW",
        "EI %s, você sabia que a egirltrans é tão burra que não sabe instalar o CheatBreaker de fontes oficiais, e então fala que é vírus? #CCW",
        "%s eu vou fazer macumba pra você morrer #CCW",
        "Exu te abençoe %s #CCW",
        "SAY WALLAHI BRO %s #CCW",
        "%s, você é a prova viva de que o cérebro é um órgão completamente opcional #CCW",
        "Eu diria para você melhorar %s, mas um transplante de cérebro não vende no Mercado Livre #CCW",
        "Cuidado com os hackers russos %s, doxei sua casa e o endereço deu num lixão! #CCW",
        "Seu psicólogo desistiu de você né %s? #CCW",
        "Você é o motivo do 4bortØ ser debatido %s. #CCW",
        "Seu futuro é mais escuro que o porão do Diddy %s. #CCW",
        "O %s desliga o monitor quando joga pra ter uma desculpa? #CCW",
        "O %s é o motivo pelo qual as pessoas preferem o isolamento social. #CCW",
        "A misantropi4 faz sentido quando a gente vê o %s tentando jogar. #CCW",
        "Sua única contribuição pro planeta vai ser virar adubo %s. #CCW"
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
                    String targetName = entity.getName();
                    sendInsult(targetName);
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
        int mode = this.chatMode.getValue();

        if (mode == 1) {
            finalMessage = "/g " + finalMessage;
        } else if (mode == 2) {
            finalMessage = "/tell " + target + " " + finalMessage;
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
